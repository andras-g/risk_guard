package hu.riskguard.epr.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EprSubmissionSummary(
        UUID id,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalWeightKg,
        BigDecimal totalFeeHuf,
        OffsetDateTime exportedAt,
        String fileName,
        String submittedByUserEmail,
        boolean hasXmlContent
) {}
