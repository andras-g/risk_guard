package hu.riskguard.screening.domain;

/**
 * Result of re-computing the SHA-256 hash for an audit log entry.
 * Used to verify that the stored hash matches what would be computed from the stored inputs.
 *
 * @param match        true if recomputed hash equals stored hash (and unavailable is false)
 * @param computedHash the freshly recomputed SHA-256 hash
 * @param storedHash   the hash stored in search_audit_log.sha256_hash
 * @param unavailable  true when verification is indeterminate — either the original hash was never
 *                     computed (HASH_UNAVAILABLE sentinel), or the snapshot data may have been
 *                     refreshed since the audit entry was written (stale-snapshot limitation)
 */
public record AuditHashVerifyResult(
        boolean match,
        String computedHash,
        String storedHash,
        boolean unavailable
) {}
