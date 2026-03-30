package hu.riskguard.screening.domain;

import java.time.LocalDate;

/**
 * Filter parameters for the audit history query.
 * All fields are nullable — null means "no filter applied" for that dimension.
 *
 * @param startDate  inclusive lower bound on searched_at (nullable)
 * @param endDate    inclusive upper bound on searched_at (nullable)
 * @param taxNumber  exact or prefix match on tax_number (nullable)
 * @param checkSource filter to 'MANUAL' or 'AUTOMATED' entries only (nullable = all)
 */
public record AuditHistoryFilter(
        LocalDate startDate,
        LocalDate endDate,
        String taxNumber,
        String checkSource
) {}
