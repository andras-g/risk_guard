package hu.riskguard.identity.domain;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.identity.internal.IdentityRepository;
import hu.riskguard.jooq.tables.records.RefreshTokensRecord;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages refresh token lifecycle: issue, validate, rotate, revoke, family revocation, and cleanup.
 *
 * <p>Refresh tokens are opaque 32-byte random strings. Only their SHA-256 hash is stored in the DB.
 * The raw token exists only in the HttpOnly cookie and transiently in memory during issuance/rotation.
 *
 * <p>Token families ({@code family_id}) group all tokens in a rotation chain originating from a single
 * login event. On reuse detection (replaying a revoked token), the entire family is revoked per OWASP
 * recommendation, forcing both attacker and legitimate user to re-authenticate.
 *
 * @see hu.riskguard.identity.domain.GuestSessionService for Clock injection pattern reference (Story 3.12)
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final IdentityRepository identityRepository;
    private final RiskGuardProperties properties;
    private final Clock clock;

    private final SecureRandom secureRandom = new SecureRandom();

    // ─── Result types ────────────────────────────────────────────────────────────

    /** Result of a refresh token validation and rotation attempt. */
    public sealed interface RotationResult {
        /** Successful rotation — contains the new raw token and associated user/tenant info. */
        record Success(String rawToken, UUID userId, UUID tenantId, UUID familyId) implements RotationResult {}
        /** Token hash not found in DB. */
        record Invalid() implements RotationResult {}
        /** Token was already revoked — reuse detected. Entire family has been revoked. */
        record FamilyRevoked(UUID userId) implements RotationResult {}
        /** Token is expired. */
        record Expired() implements RotationResult {}
    }

    // ─── Public API ──────────────────────────────────────────────────────────────

    /**
     * Issue a new refresh token for a user. Generates a new family_id (new login session).
     *
     * @param userId   the user's UUID
     * @param tenantId the user's home tenant ID (refresh tokens are user-scoped, not tenant-scoped)
     * @return the raw opaque token (to be set as HttpOnly cookie)
     */
    public String issueRefreshToken(UUID userId, UUID tenantId) {
        UUID familyId = UUID.randomUUID();
        return issueTokenInFamily(userId, tenantId, familyId);
    }

    /**
     * Validate and rotate a refresh token. This is the core refresh flow:
     * <ol>
     *   <li>Look up the token hash with FOR UPDATE lock</li>
     *   <li>If not found → INVALID</li>
     *   <li>If revoked → REUSE DETECTED → revoke entire family → FAMILY_REVOKED</li>
     *   <li>If expired → EXPIRED</li>
     *   <li>If valid → revoke old token, issue new one in same family → SUCCESS</li>
     * </ol>
     */
    @Transactional
    public RotationResult validateAndRotate(String rawToken) {
        String tokenHash = hashToken(rawToken);
        Optional<RefreshTokensRecord> recordOpt = identityRepository.findByTokenHashForUpdate(tokenHash);

        if (recordOpt.isEmpty()) {
            return new RotationResult.Invalid();
        }

        RefreshTokensRecord record = recordOpt.get();

        // Reuse detection: token is revoked but someone is replaying it
        if (record.getRevokedAt() != null) {
            log.warn("Refresh token reuse detected for user_id={}, family_id={}. Revoking entire family.",
                    record.getUserId(), record.getFamilyId());
            identityRepository.revokeByFamilyId(record.getFamilyId());
            return new RotationResult.FamilyRevoked(record.getUserId());
        }

        // Expired check
        if (record.getExpiresAt().isBefore(OffsetDateTime.now(clock))) {
            return new RotationResult.Expired();
        }

        // Valid token — rotate: revoke old, issue new in same family
        identityRepository.revokeByTokenHash(tokenHash);
        String newRawToken = issueTokenInFamily(record.getUserId(), record.getTenantId(), record.getFamilyId());

        return new RotationResult.Success(newRawToken, record.getUserId(), record.getTenantId(), record.getFamilyId());
    }

    /**
     * Revoke a single refresh token by its raw value.
     */
    public void revokeToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        identityRepository.revokeByTokenHash(tokenHash);
    }

    /**
     * Revoke ALL refresh tokens for a user. Used for administrative session termination.
     */
    public void revokeAllForUser(UUID userId) {
        int revoked = identityRepository.revokeAllByUserId(userId);
        if (revoked > 0) {
            log.info("Revoked {} refresh tokens for user_id={}", revoked, userId);
        }
    }

    /**
     * Revoke all tokens in a rotation family.
     */
    public void revokeFamilyByFamilyId(UUID familyId) {
        identityRepository.revokeByFamilyId(familyId);
    }

    /**
     * Scheduled cleanup: delete expired refresh tokens daily at 3:15 AM.
     * Offset from guest session cleanup at 3:00 AM to avoid concurrent heavy deletes.
     */
    @Scheduled(cron = "${risk-guard.security.refresh-token-cleanup-cron:0 15 3 * * *}")
    @Transactional
    public void cleanupExpired() {
        int deleted = identityRepository.deleteExpiredRefreshTokens();
        if (deleted > 0) {
            log.info("Cleaned up {} expired refresh tokens", deleted);
        }
    }

    // ─── Internal helpers ────────────────────────────────────────────────────────

    /**
     * Issue a new token within an existing family (for rotation) or a new family (for fresh login).
     */
    private String issueTokenInFamily(UUID userId, UUID tenantId, UUID familyId) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = hashToken(rawToken);

        OffsetDateTime expiresAt = OffsetDateTime.now(clock)
                .plusDays(properties.getSecurity().getRefreshTokenExpirationDays());

        identityRepository.insertRefreshToken(UUID.randomUUID(), userId, tenantId, tokenHash, familyId, expiresAt);

        return rawToken;
    }

    /**
     * Compute SHA-256 hex digest of a raw token string.
     * Uses standard JDK MessageDigest — NOT HashUtil which is for audit trail hashing.
     */
    static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by the JDK specification — this should never happen
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
