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
        String taxNumber
) {}
