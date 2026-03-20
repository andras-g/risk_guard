package hu.riskguard.identity.domain;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.identity.internal.IdentityRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages guest session lifecycle: creation, fingerprint lookup, limit checks,
 * counter increments, daily reset logic, and scheduled cleanup.
 *
 * <p>Guest sessions use a synthetic {@code tenant_id} derived deterministically
 * from the session ID: {@code UUID.nameUUIDFromBytes(("guest-" + sessionId).getBytes())}.
 * This ensures guest data is isolated from authenticated user data.
 *
 * <p>Limits are sourced from {@link RiskGuardProperties.Guest} (bound from
 * {@code risk-guard-tokens.json} / {@code application.yml}).
 */
@Service
@RequiredArgsConstructor
public class GuestSessionService {

    private static final Logger log = LoggerFactory.getLogger(GuestSessionService.class);

    private final IdentityRepository identityRepository;
    private final RiskGuardProperties properties;
    private final Clock clock;

    /**
     * Find or create a guest session by fingerprint.
     * If a non-expired session exists for the fingerprint, return it (with daily reset applied).
     * Otherwise, create a new session.
     *
     * @param sessionFingerprint SHA-256 hash of browser fingerprint
     * @return the guest session (existing or newly created)
     */
    @Transactional
    public GuestSession findOrCreateSession(String sessionFingerprint) {
        Optional<GuestSession> existing = identityRepository.findGuestSessionByFingerprintForUpdate(sessionFingerprint);

        if (existing.isPresent()) {
            GuestSession session = existing.get();
            // Apply daily reset if the session was created on a different day
            session = applyDailyResetIfNeeded(session);
            return session;
        }

        // Create new session
        UUID sessionId = UUID.randomUUID();
        UUID syntheticTenantId = generateSyntheticTenantId(sessionId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime expiresAt = now.plusHours(24);

        identityRepository.createGuestSession(sessionId, syntheticTenantId, sessionFingerprint, now, expiresAt);

        return new GuestSession(sessionId, syntheticTenantId, sessionFingerprint, 0, 0, now, expiresAt);
    }

    /**
     * Check if the guest session has reached any rate limits.
     *
     * @param session the guest session to check
     * @return the limit status (OK, COMPANY_LIMIT_REACHED, or DAILY_LIMIT_REACHED)
     */
    public GuestLimitStatus checkLimits(GuestSession session) {
        int maxCompanies = properties.getGuest().getMaxCompanies();
        int maxDailyChecks = properties.getGuest().getMaxDailyChecks();

        if (session.companiesChecked() >= maxCompanies) {
            return GuestLimitStatus.COMPANY_LIMIT_REACHED;
        }
        if (session.dailyChecks() >= maxDailyChecks) {
            return GuestLimitStatus.DAILY_LIMIT_REACHED;
        }
        return GuestLimitStatus.OK;
    }

    /**
     * Increment the companies_checked counter (only for new unique tax numbers)
     * and the daily_checks counter.
     *
     * @param sessionId the guest session ID
     * @param isNewCompany true if this is a new unique tax number for this session
     */
    @Transactional
    public void incrementCounters(UUID sessionId, boolean isNewCompany) {
        identityRepository.incrementGuestDailyChecks(sessionId);
        if (isNewCompany) {
            identityRepository.incrementGuestCompaniesChecked(sessionId);
        }
    }

    /**
     * Get the current usage statistics for a guest session.
     *
     * @return max companies and max daily checks from configuration
     */
    public int getMaxCompanies() {
        return properties.getGuest().getMaxCompanies();
    }

    public int getMaxDailyChecks() {
        return properties.getGuest().getMaxDailyChecks();
    }

    /**
     * Generate a deterministic synthetic tenant ID for a guest session.
     * Format: UUID derived from "guest-{sessionId}" bytes.
     */
    public static UUID generateSyntheticTenantId(UUID sessionId) {
        return UUID.nameUUIDFromBytes(("guest-" + sessionId).getBytes());
    }

    /**
     * Apply daily reset: if the session's created_at date differs from today,
     * reset daily_checks to 0.
     */
    private GuestSession applyDailyResetIfNeeded(GuestSession session) {
        LocalDate sessionDate = session.createdAt().toLocalDate();
        LocalDate today = LocalDate.now(clock);

        if (!sessionDate.equals(today)) {
            identityRepository.resetGuestDailyChecks(session.id());
            return new GuestSession(
                    session.id(), session.tenantId(), session.sessionFingerprint(),
                    session.companiesChecked(), 0, session.createdAt(), session.expiresAt()
            );
        }
        return session;
    }

    /**
     * Scheduled cleanup: purge expired guest sessions daily at 3 AM.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredSessions() {
        int deleted = identityRepository.deleteExpiredGuestSessions();
        if (deleted > 0) {
            log.info("Cleaned up {} expired guest sessions", deleted);
        }
    }
}
