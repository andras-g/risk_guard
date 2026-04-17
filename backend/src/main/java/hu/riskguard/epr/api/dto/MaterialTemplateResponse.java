package hu.riskguard.epr.api.dto;

import hu.riskguard.jooq.tables.records.EprMaterialTemplatesRecord;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a material template.
 * Maps from jOOQ {@link EprMaterialTemplatesRecord} via the {@link #from(EprMaterialTemplatesRecord)} factory.
 *
 * @param id               template UUID
 * @param name             template display name
 * @param baseWeightGrams  base weight in grams
 * @param kfCode           KF code (null if not yet classified)
 * @param verified         whether the template has been verified with a KF code
 * @param recurring        whether this material is automatically included each quarter
 * @param createdAt        creation timestamp
 * @param updatedAt        last update timestamp
 * @param overrideKfCode   manually overridden KF-code (null if no override)
 * @param overrideReason   free-text reason for override (null if no override)
 * @param confidence            confidence level of the latest calculation (null if not yet classified)
 * @param feeRate               fee rate from the latest linked calculation (null if not yet classified)
 * @param materialClassification human-readable classification path from the latest calculation (null if not yet classified)
 */
public record MaterialTemplateResponse(
        UUID id,
        String name,
        BigDecimal baseWeightGrams,
        String kfCode,
        boolean verified,
        boolean recurring,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String overrideKfCode,
        String overrideReason,
        String confidence,
        BigDecimal feeRate,
        String materialClassification
) {

    /**
     * Map from jOOQ record to API response DTO (without override metadata).
     */
    public static MaterialTemplateResponse from(EprMaterialTemplatesRecord record) {
        return new MaterialTemplateResponse(
                record.getId(),
                record.getName(),
                record.getBaseWeightGrams(),
                record.getKfCode(),
                record.getVerified(),
                record.getRecurring(),
                record.getCreatedAt(),
                record.getUpdatedAt(),
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Map from jOOQ record with override metadata from a LEFT JOIN query.
     */
    public static MaterialTemplateResponse from(EprMaterialTemplatesRecord record,
                                                  String overrideKfCode,
                                                  String overrideReason,
                                                  String confidence,
                                                  BigDecimal feeRate,
                                                  String materialClassification) {
        return new MaterialTemplateResponse(
                record.getId(),
                record.getName(),
                record.getBaseWeightGrams(),
                record.getKfCode(),
                record.getVerified(),
                record.getRecurring(),
                record.getCreatedAt(),
                record.getUpdatedAt(),
                overrideKfCode,
                overrideReason,
                confidence,
                feeRate,
                materialClassification
        );
    }
}
