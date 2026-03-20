package hu.riskguard.identity.domain;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.identity.internal.IdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GuestSessionService — guest session lifecycle management.
 * Tests: session creation, fingerprint lookup, limit checks, counter increment,
 * daily reset logic, and synthetic tenant ID generation.
 */
@ExtendWith(MockitoExtension.class)
class GuestSessionServiceTest {

    @Mock
    private IdentityRepository identityRepository;

    private GuestSessionService guestSessionService;

    private static final int MAX_COMPANIES = 10;
    private static final int MAX_DAILY_CHECKS = 3;

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-03-20T10:00:00Z"), ZoneId.of("Europe/Budapest"));

    @BeforeEach
    void setUp() {
        RiskGuardProperties properties = new RiskGuardProperties();
        properties.getGuest().setMaxCompanies(MAX_COMPANIES);
        properties.getGuest().setMaxDailyChecks(MAX_DAILY_CHECKS);
        guestSessionService = new GuestSessionService(identityRepository, properties, FIXED_CLOCK);
    }

    // ─── Session Creation ────────────────────────────────────────────────────

    @Test
    void findOrCreateSessionShouldCreateNewSessionWhenNoneExists() {
        // Given
        String fingerprint = "abc123hash";
        when(identityRepository.findGuestSessionByFingerprintForUpdate(fingerprint))
                .thenReturn(Optional.empty());

        // When
        GuestSession session = guestSessionService.findOrCreateSession(fingerprint);

        // Then
        assertThat(session).isNotNull();
        assertThat(session.id()).isNotNull();
        assertThat(session.tenantId()).isNotNull();
        assertThat(session.sessionFingerprint()).isEqualTo(fingerprint);
        assertThat(session.companiesChecked()).isEqualTo(0);
        assertThat(session.dailyChecks()).isEqualTo(0);
        assertThat(session.expiresAt()).isAfter(OffsetDateTime.now(FIXED_CLOCK));
        verify(identityRepository).createGuestSession(
                any(UUID.class), any(UUID.class), eq(fingerprint),
                any(OffsetDateTime.class), any(OffsetDateTime.class));
    }

    @Test
    void findOrCreateSessionShouldReturnExistingSessionWhenFingerprintMatches() {
        // Given
        String fingerprint = "abc123hash";
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = GuestSessionService.generateSyntheticTenantId(sessionId);
        OffsetDateTime now = OffsetDateTime.now(FIXED_CLOCK);
        GuestSession existing = new GuestSession(
                sessionId, tenantId, fingerprint, 3, 2,
                now, now.plusHours(24));
        when(identityRepository.findGuestSessionByFingerprintForUpdate(fingerprint))
                .thenReturn(Optional.of(existing));

        // When
        GuestSession session = guestSessionService.findOrCreateSession(fingerprint);

        // Then
        assertThat(session.id()).isEqualTo(sessionId);
        assertThat(session.companiesChecked()).isEqualTo(3);
        assertThat(session.dailyChecks()).isEqualTo(2);
        verify(identityRepository, never()).createGuestSession(
                any(), any(), any(), any(), any());
    }

    // ─── Daily Reset Logic ───────────────────────────────────────────────────

    @Test
    void findOrCreateSessionShouldResetDailyChecksWhenDayChanged() {
        // Given — session created yesterday
        String fingerprint = "abc123hash";
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = GuestSessionService.generateSyntheticTenantId(sessionId);
        OffsetDateTime now = OffsetDateTime.now(FIXED_CLOCK);
        GuestSession yesterday = new GuestSession(
                sessionId, tenantId, fingerprint, 5, 3,
                now.minusDays(1), now.plusHours(12));
        when(identityRepository.findGuestSessionByFingerprintForUpdate(fingerprint))
                .thenReturn(Optional.of(yesterday));

        // When
        GuestSession session = guestSessionService.findOrCreateSession(fingerprint);

        // Then — daily checks reset to 0, companies_checked unchanged
        assertThat(session.dailyChecks()).isEqualTo(0);
        assertThat(session.companiesChecked()).isEqualTo(5);
        verify(identityRepository).resetGuestDailyChecks(sessionId);
    }

    @Test
    void findOrCreateSessionShouldNotResetDailyChecksOnSameDay() {
        // Given — session created today (same day as FIXED_CLOCK)
        String fingerprint = "abc123hash";
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = GuestSessionService.generateSyntheticTenantId(sessionId);
        OffsetDateTime now = OffsetDateTime.now(FIXED_CLOCK);
        GuestSession today = new GuestSession(
                sessionId, tenantId, fingerprint, 2, 1,
                now, now.plusHours(24));
        when(identityRepository.findGuestSessionByFingerprintForUpdate(fingerprint))
                .thenReturn(Optional.of(today));

        // When
        GuestSession session = guestSessionService.findOrCreateSession(fingerprint);

        // Then — no reset
        assertThat(session.dailyChecks()).isEqualTo(1);
        verify(identityRepository, never()).resetGuestDailyChecks(any());
    }

    // ─── Limit Checks ────────────────────────────────────────────────────────

    @Test
    void checkLimitsShouldReturnOkWhenUnderLimits() {
        // Given
        GuestSession session = createSession(3, 1);

        // When
        GuestLimitStatus status = guestSessionService.checkLimits(session);

        // Then
        assertThat(status).isEqualTo(GuestLimitStatus.OK);
    }

    @Test
    void checkLimitsShouldReturnCompanyLimitReachedWhenAtMax() {
        // Given — 10 companies checked (max is 10)
        GuestSession session = createSession(MAX_COMPANIES, 1);

        // When
        GuestLimitStatus status = guestSessionService.checkLimits(session);

        // Then
        assertThat(status).isEqualTo(GuestLimitStatus.COMPANY_LIMIT_REACHED);
    }

    @Test
    void checkLimitsShouldReturnDailyLimitReachedWhenAtMax() {
        // Given — 3 daily checks (max is 3)
        GuestSession session = createSession(5, MAX_DAILY_CHECKS);

        // When
        GuestLimitStatus status = guestSessionService.checkLimits(session);

        // Then
        assertThat(status).isEqualTo(GuestLimitStatus.DAILY_LIMIT_REACHED);
    }

    @Test
    void checkLimitsShouldPrioritizeCompanyLimitOverDailyLimit() {
        // Given — both limits reached
        GuestSession session = createSession(MAX_COMPANIES, MAX_DAILY_CHECKS);

        // When
        GuestLimitStatus status = guestSessionService.checkLimits(session);

        // Then — company limit checked first
        assertThat(status).isEqualTo(GuestLimitStatus.COMPANY_LIMIT_REACHED);
    }

    // ─── Counter Increment ───────────────────────────────────────────────────

    @Test
    void incrementCountersShouldIncrementBothForNewCompany() {
        // Given
        UUID sessionId = UUID.randomUUID();

        // When
        guestSessionService.incrementCounters(sessionId, true);

        // Then
        verify(identityRepository).incrementGuestDailyChecks(sessionId);
        verify(identityRepository).incrementGuestCompaniesChecked(sessionId);
    }

    @Test
    void incrementCountersShouldOnlyIncrementDailyForExistingCompany() {
        // Given
        UUID sessionId = UUID.randomUUID();

        // When
        guestSessionService.incrementCounters(sessionId, false);

        // Then
        verify(identityRepository).incrementGuestDailyChecks(sessionId);
        verify(identityRepository, never()).incrementGuestCompaniesChecked(sessionId);
    }

    // ─── Synthetic Tenant ID ─────────────────────────────────────────────────

    @Test
    void generateSyntheticTenantIdShouldBeDeterministic() {
        // Given
        UUID sessionId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        // When
        UUID tenantId1 = GuestSessionService.generateSyntheticTenantId(sessionId);
        UUID tenantId2 = GuestSessionService.generateSyntheticTenantId(sessionId);

        // Then
        assertThat(tenantId1).isEqualTo(tenantId2);
        assertThat(tenantId1).isNotEqualTo(sessionId);
    }

    @Test
    void generateSyntheticTenantIdShouldDifferForDifferentSessions() {
        // Given
        UUID sessionA = UUID.randomUUID();
        UUID sessionB = UUID.randomUUID();

        // When
        UUID tenantA = GuestSessionService.generateSyntheticTenantId(sessionA);
        UUID tenantB = GuestSessionService.generateSyntheticTenantId(sessionB);

        // Then
        assertThat(tenantA).isNotEqualTo(tenantB);
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private GuestSession createSession(int companiesChecked, int dailyChecks) {
        UUID sessionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(FIXED_CLOCK);
        return new GuestSession(
                sessionId,
                GuestSessionService.generateSyntheticTenantId(sessionId),
                "test-fingerprint",
                companiesChecked,
                dailyChecks,
                now,
                now.plusHours(24)
        );
    }
}
