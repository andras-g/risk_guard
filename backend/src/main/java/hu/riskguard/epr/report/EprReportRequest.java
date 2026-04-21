package hu.riskguard.epr.report;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Input parameters for an EPR report generation request.
 */
public record EprReportRequest(
        UUID tenantId,
        LocalDate periodStart,
        LocalDate periodEnd,
        String taxNumber,
        UUID submittedByUserId
) {
    /** Convenience constructor for preview/test paths that have no user context. */
    public EprReportRequest(UUID tenantId, LocalDate periodStart, LocalDate periodEnd, String taxNumber) {
        this(tenantId, periodStart, periodEnd, taxNumber, null);
    }
}
