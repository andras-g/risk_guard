package hu.riskguard.identity.domain;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.identity.internal.IdentityRepository;
import hu.riskguard.jooq.tables.records.RefreshTokensRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 * Unit tests for {@link RefreshTokenService} — refresh token lifecycle.
 * Story 3.13 — Task 10.1
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private IdentityRepository identityRepository;

    private RiskGuardProperties properties;
    private Clock clock;
    private RefreshTokenService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final Instant FIXED_INSTANT = Instant.parse("2026-03-20T12:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        properties = new RiskGuardProperties();
        properties.getSecurity().setRefreshTokenExpirationDays(30);
        service = new RefreshTokenService(identityRepository, properties, clock);
    }

    // --- Token Issuance ---

    @Test
    void issueRefreshToken_shouldStoreHashedTokenInDb() {
        String rawToken = service.issueRefreshToken(USER_ID, TENANT_ID);

        assertThat(rawToken).isNotNull().isNotBlank();

        // Verify repository was called with a hash, not the raw token
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(identityRepository).insertRefreshToken(
                any(UUID.class), eq(USER_ID), eq(TENANT_ID),
                hashCaptor.capture(), any(UUID.class), any(OffsetDateTime.class));

        String storedHash = hashCaptor.getValue();
        // Hash should be 64 hex chars (SHA-256)
        assertThat(storedHash).hasSize(64);
        // Hash should NOT be the raw token
        assertThat(storedHash).isNotEqualTo(rawToken);
    }

    @Test
    void issueRefreshToken_shouldSetExpirationTo30Days() {
        service.issueRefreshToken(USER_ID, TENANT_ID);

        ArgumentCaptor<OffsetDateTime> expiresCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(identityRepository).insertRefreshToken(
                any(), any(), any(), any(), any(), expiresCaptor.capture());

        OffsetDateTime expected = OffsetDateTime.now(clock).plusDays(30);
        assertThat(expiresCaptor.getValue()).isEqualTo(expected);
    }

    @Test
    void issueRefreshToken_shouldGenerateNewFamilyId() {
        service.issueRefreshToken(USER_ID, TENANT_ID);

        ArgumentCaptor<UUID> familyCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(identityRepository).insertRefreshToken(
                any(), any(), any(), any(), familyCaptor.capture(), any());

        assertThat(familyCaptor.getValue()).isNotNull();
    }

    // --- Token Rotation ---

    @Test
    void validateAndRotate_validToken_shouldReturnSuccessAndRevokeOld() {
        String rawToken = "test-raw-token-value";
        String tokenHash = RefreshTokenService.hashToken(rawToken);
        UUID familyId = UUID.randomUUID();

        RefreshTokensRecord record = createRecord(tokenHash, familyId, null,
                OffsetDateTime.now(clock).plusDays(29));
        when(identityRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(record));

        RefreshTokenService.RotationResult result = service.validateAndRotate(rawToken);

        assertThat(result).isInstanceOf(RefreshTokenService.RotationResult.Success.class);
        var success = (RefreshTokenService.RotationResult.Success) result;
        assertThat(success.userId()).isEqualTo(USER_ID);
        assertThat(success.tenantId()).isEqualTo(TENANT_ID);
        assertThat(success.familyId()).isEqualTo(familyId);
        assertThat(success.rawToken()).isNotNull().isNotEqualTo(rawToken);

        // Old token should be revoked
        verify(identityRepository).revokeByTokenHash(tokenHash);
        // New token should be inserted with same family_id
        verify(identityRepository).insertRefreshToken(
                any(), eq(USER_ID), eq(TENANT_ID), anyString(), eq(familyId), any());
    }

    // --- Reuse Detection ---

    @Test
    void validateAndRotate_revokedToken_shouldRevokeFamilyAndReturnFamilyRevoked() {
        String rawToken = "stolen-token";
        String tokenHash = RefreshTokenService.hashToken(rawToken);
        UUID familyId = UUID.randomUUID();

        RefreshTokensRecord record = createRecord(tokenHash, familyId,
                OffsetDateTime.now(clock).minusHours(1), // already revoked
                OffsetDateTime.now(clock).plusDays(29));
        when(identityRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(record));

        RefreshTokenService.RotationResult result = service.validateAndRotate(rawToken);

        assertThat(result).isInstanceOf(RefreshTokenService.RotationResult.FamilyRevoked.class);
        var revoked = (RefreshTokenService.RotationResult.FamilyRevoked) result;
        assertThat(revoked.userId()).isEqualTo(USER_ID);

        // Entire family should be revoked
        verify(identityRepository).revokeByFamilyId(familyId);
        // No new token should be issued
        verify(identityRepository, never()).insertRefreshToken(any(), any(), any(), any(), any(), any());
    }

    // --- Expired Token ---

    @Test
    void validateAndRotate_expiredToken_shouldReturnExpired() {
        String rawToken = "expired-token";
        String tokenHash = RefreshTokenService.hashToken(rawToken);
        UUID familyId = UUID.randomUUID();

        RefreshTokensRecord record = createRecord(tokenHash, familyId, null,
                OffsetDateTime.now(clock).minusHours(1)); // already expired
        when(identityRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(record));

        RefreshTokenService.RotationResult result = service.validateAndRotate(rawToken);

        assertThat(result).isInstanceOf(RefreshTokenService.RotationResult.Expired.class);
        verify(identityRepository, never()).revokeByTokenHash(any());
        verify(identityRepository, never()).insertRefreshToken(any(), any(), any(), any(), any(), any());
    }

    // --- Invalid Token ---

    @Test
    void validateAndRotate_unknownToken_shouldReturnInvalid() {
        String rawToken = "unknown-token";
        String tokenHash = RefreshTokenService.hashToken(rawToken);
        when(identityRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.empty());

        RefreshTokenService.RotationResult result = service.validateAndRotate(rawToken);

        assertThat(result).isInstanceOf(RefreshTokenService.RotationResult.Invalid.class);
    }

    // --- Revoke Single ---

    @Test
    void revokeToken_shouldCallRepositoryWithHash() {
        String rawToken = "token-to-revoke";
        service.revokeToken(rawToken);

        String expectedHash = RefreshTokenService.hashToken(rawToken);
        verify(identityRepository).revokeByTokenHash(expectedHash);
    }

    // --- Revoke All For User ---

    @Test
    void revokeAllForUser_shouldDelegateToRepository() {
        service.revokeAllForUser(USER_ID);
        verify(identityRepository).revokeAllByUserId(USER_ID);
    }

    // --- Cleanup ---

    @Test
    void cleanupExpired_shouldDelegateToRepository() {
        when(identityRepository.deleteExpiredRefreshTokens()).thenReturn(5);
        service.cleanupExpired();
        verify(identityRepository).deleteExpiredRefreshTokens();
    }

    // --- Hash Determinism ---

    @Test
    void hashToken_shouldBeDeterministic() {
        String hash1 = RefreshTokenService.hashToken("same-input");
        String hash2 = RefreshTokenService.hashToken("same-input");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hashToken_shouldProduceDifferentHashForDifferentInput() {
        String hash1 = RefreshTokenService.hashToken("input-a");
        String hash2 = RefreshTokenService.hashToken("input-b");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    // --- Helpers ---

    private RefreshTokensRecord createRecord(String tokenHash, UUID familyId,
                                              OffsetDateTime revokedAt, OffsetDateTime expiresAt) {
        RefreshTokensRecord record = new RefreshTokensRecord();
        record.setId(UUID.randomUUID());
        record.setUserId(USER_ID);
        record.setTenantId(TENANT_ID);
        record.setTokenHash(tokenHash);
        record.setFamilyId(familyId);
        record.setRevokedAt(revokedAt);
        record.setExpiresAt(expiresAt);
        record.setCreatedAt(OffsetDateTime.now(clock));
        return record;
    }
}
