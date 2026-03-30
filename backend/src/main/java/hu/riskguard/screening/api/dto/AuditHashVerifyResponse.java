package hu.riskguard.screening.api.dto;

import hu.riskguard.screening.domain.AuditHashVerifyResult;

/**
 * Response for {@code GET /api/screening/audit-history/{id}/verify-hash}.
 */
public record AuditHashVerifyResponse(
        boolean match,
        String computedHash,
        String storedHash,
        boolean unavailable
) {
    public static AuditHashVerifyResponse from(AuditHashVerifyResult result) {
        return new AuditHashVerifyResponse(result.match(), result.computedHash(), result.storedHash(),
                result.unavailable());
    }
}
