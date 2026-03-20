package hu.riskguard.identity.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain record representing a guest session with usage statistics.
 * Standalone type exported through the {@link IdentityService} facade.
 *
 * <p>Guest sessions use a synthetic {@code tenant_id} derived deterministically
 * from the session ID to isolate guest data from authenticated user data.
 *
 * @param id                  guest session UUID
 * @param tenantId            synthetic tenant UUID (deterministic from session ID)
 * @param sessionFingerprint  browser fingerprint hash for session continuity
 * @param companiesChecked    number of unique companies searched in this session
 * @param dailyChecks         number of checks performed today
 * @param createdAt           session creation timestamp
 * @param expiresAt           session expiry timestamp (24h from creation)
 */
public record GuestSession(
        UUID id,
        UUID tenantId,
        String sessionFingerprint,
        int companiesChecked,
        int dailyChecks,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {}
