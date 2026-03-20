package hu.riskguard.screening.api;

import hu.riskguard.identity.domain.GuestLimitStatus;
import hu.riskguard.identity.domain.GuestSession;
import hu.riskguard.identity.domain.GuestSessionService;
import hu.riskguard.identity.domain.IdentityService;
import hu.riskguard.jooq.enums.VerdictConfidence;
import hu.riskguard.jooq.enums.VerdictStatus;
import hu.riskguard.screening.api.dto.GuestLimitResponse;
import hu.riskguard.screening.api.dto.GuestSearchRequest;
import hu.riskguard.screening.api.dto.GuestSearchResponse;
import hu.riskguard.screening.domain.ScreeningService;
import hu.riskguard.screening.domain.ScreeningService.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for guest search endpoint (Story 3.12).
 * Tests: successful guest search, 429 on company limit, 429 on daily limit,
 * session creation on first search.
 */
@ExtendWith(MockitoExtension.class)
class GuestSearchEndpointTest {

    @Mock
    private IdentityService identityService;
    @Mock
    private ScreeningService screeningService;

    private GuestSearchController controller;

    private static final String FINGERPRINT = "sha256-browser-fingerprint";
    private static final String TAX_NUMBER = "12345678";
    private static final int MAX_COMPANIES = 10;
    private static final int MAX_DAILY_CHECKS = 3;
    private static final OffsetDateTime FIXED_TIME = OffsetDateTime.parse("2026-03-20T10:00:00+01:00");

    @BeforeEach
    void setUp() {
        controller = new GuestSearchController(identityService, screeningService);
    }

    // ─── Successful Search ───────────────────────────────────────────────────

    @Test
    void guestSearchShouldReturnVerdictWithUsageStats() {
        // Given
        GuestSearchRequest request = new GuestSearchRequest(TAX_NUMBER, FINGERPRINT);
        GuestSession session = createSession(2, 1);

        when(identityService.findOrCreateGuestSession(FINGERPRINT)).thenReturn(session);
        when(identityService.checkGuestLimits(session)).thenReturn(GuestLimitStatus.OK);
        when(screeningService.hasSnapshotForTenant(session.tenantId(), TAX_NUMBER))
                .thenReturn(false);
        when(identityService.getGuestMaxCompanies()).thenReturn(MAX_COMPANIES);
        when(identityService.getGuestMaxDailyChecks()).thenReturn(MAX_DAILY_CHECKS);

        SearchResult searchResult = new SearchResult(
                UUID.randomUUID(), UUID.randomUUID(), TAX_NUMBER,
                VerdictStatus.RELIABLE, VerdictConfidence.FRESH, FIXED_TIME,
                List.of(), false, "Test Company Kft.", "abc123hash"
        );
        when(screeningService.search(eq(TAX_NUMBER), any(UUID.class), eq(session.tenantId())))
                .thenReturn(searchResult);

        // When
        ResponseEntity<?> response = controller.guestSearch(request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        GuestSearchResponse body = (GuestSearchResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.taxNumber()).isEqualTo(TAX_NUMBER);
        assertThat(body.status()).isEqualTo("RELIABLE");
        assertThat(body.companiesUsed()).isEqualTo(3); // 2 + 1 (new)
        assertThat(body.companiesLimit()).isEqualTo(MAX_COMPANIES);
        assertThat(body.dailyChecksUsed()).isEqualTo(2); // 1 + 1
        assertThat(body.dailyChecksLimit()).isEqualTo(MAX_DAILY_CHECKS);

        verify(identityService).incrementGuestCounters(session.id(), true);
    }

    @Test
    void guestSearchForExistingCompanyShouldNotIncrementCompaniesChecked() {
        // Given — same tax number searched before
        GuestSearchRequest request = new GuestSearchRequest(TAX_NUMBER, FINGERPRINT);
        GuestSession session = createSession(5, 1);

        when(identityService.findOrCreateGuestSession(FINGERPRINT)).thenReturn(session);
        when(identityService.checkGuestLimits(session)).thenReturn(GuestLimitStatus.OK);
        when(screeningService.hasSnapshotForTenant(session.tenantId(), TAX_NUMBER))
                .thenReturn(true); // Already searched
        when(identityService.getGuestMaxCompanies()).thenReturn(MAX_COMPANIES);
        when(identityService.getGuestMaxDailyChecks()).thenReturn(MAX_DAILY_CHECKS);

        SearchResult searchResult = new SearchResult(
                UUID.randomUUID(), UUID.randomUUID(), TAX_NUMBER,
                VerdictStatus.RELIABLE, VerdictConfidence.FRESH, FIXED_TIME,
                List.of(), false, "Test Company Kft.", "abc123hash"
        );
        when(screeningService.search(eq(TAX_NUMBER), any(UUID.class), eq(session.tenantId())))
                .thenReturn(searchResult);

        // When
        ResponseEntity<?> response = controller.guestSearch(request);

        // Then
        GuestSearchResponse body = (GuestSearchResponse) response.getBody();
        assertThat(body.companiesUsed()).isEqualTo(5); // unchanged — existing company
        assertThat(body.dailyChecksUsed()).isEqualTo(2); // 1 + 1

        verify(identityService).incrementGuestCounters(session.id(), false);
    }

    // ─── Rate Limiting (429) ─────────────────────────────────────────────────

    @Test
    void guestSearchShouldReturn429WhenCompanyLimitReached() {
        // Given
        GuestSearchRequest request = new GuestSearchRequest(TAX_NUMBER, FINGERPRINT);
        GuestSession session = createSession(MAX_COMPANIES, 1);

        when(identityService.findOrCreateGuestSession(FINGERPRINT)).thenReturn(session);
        when(identityService.checkGuestLimits(session)).thenReturn(GuestLimitStatus.COMPANY_LIMIT_REACHED);
        when(identityService.getGuestMaxCompanies()).thenReturn(MAX_COMPANIES);

        // When
        ResponseEntity<?> response = controller.guestSearch(request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        GuestLimitResponse body = (GuestLimitResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.error()).isEqualTo("COMPANY_LIMIT_REACHED");
        assertThat(body.companiesUsed()).isEqualTo(MAX_COMPANIES);
        assertThat(body.companiesLimit()).isEqualTo(MAX_COMPANIES);

        // Verify no search was performed
        verify(screeningService, never()).search(any(), any(), any());
    }

    @Test
    void guestSearchShouldReturn429WhenDailyLimitReached() {
        // Given
        GuestSearchRequest request = new GuestSearchRequest(TAX_NUMBER, FINGERPRINT);
        GuestSession session = createSession(5, MAX_DAILY_CHECKS);

        when(identityService.findOrCreateGuestSession(FINGERPRINT)).thenReturn(session);
        when(identityService.checkGuestLimits(session)).thenReturn(GuestLimitStatus.DAILY_LIMIT_REACHED);
        when(identityService.getGuestMaxDailyChecks()).thenReturn(MAX_DAILY_CHECKS);

        // When
        ResponseEntity<?> response = controller.guestSearch(request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        GuestLimitResponse body = (GuestLimitResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.error()).isEqualTo("DAILY_LIMIT_REACHED");
        assertThat(body.dailyChecksUsed()).isEqualTo(MAX_DAILY_CHECKS);
        assertThat(body.dailyChecksLimit()).isEqualTo(MAX_DAILY_CHECKS);

        verify(screeningService, never()).search(any(), any(), any());
    }

    // ─── Session Creation ────────────────────────────────────────────────────

    @Test
    void guestSearchShouldCreateSessionOnFirstSearch() {
        // Given — new fingerprint, no existing session
        GuestSearchRequest request = new GuestSearchRequest(TAX_NUMBER, FINGERPRINT);
        GuestSession newSession = createSession(0, 0);

        when(identityService.findOrCreateGuestSession(FINGERPRINT)).thenReturn(newSession);
        when(identityService.checkGuestLimits(newSession)).thenReturn(GuestLimitStatus.OK);
        when(screeningService.hasSnapshotForTenant(newSession.tenantId(), TAX_NUMBER))
                .thenReturn(false);
        when(identityService.getGuestMaxCompanies()).thenReturn(MAX_COMPANIES);
        when(identityService.getGuestMaxDailyChecks()).thenReturn(MAX_DAILY_CHECKS);

        SearchResult searchResult = new SearchResult(
                UUID.randomUUID(), UUID.randomUUID(), TAX_NUMBER,
                VerdictStatus.RELIABLE, VerdictConfidence.FRESH, FIXED_TIME,
                List.of(), false, "New Company Kft.", "hash123"
        );
        when(screeningService.search(eq(TAX_NUMBER), any(UUID.class), eq(newSession.tenantId())))
                .thenReturn(searchResult);

        // When
        ResponseEntity<?> response = controller.guestSearch(request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        GuestSearchResponse body = (GuestSearchResponse) response.getBody();
        assertThat(body.companiesUsed()).isEqualTo(1); // First company
        assertThat(body.dailyChecksUsed()).isEqualTo(1); // First check
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private GuestSession createSession(int companiesChecked, int dailyChecks) {
        UUID sessionId = UUID.randomUUID();
        return new GuestSession(
                sessionId,
                GuestSessionService.generateSyntheticTenantId(sessionId),
                FINGERPRINT,
                companiesChecked,
                dailyChecks,
                FIXED_TIME,
                FIXED_TIME.plusHours(24)
        );
    }
}
