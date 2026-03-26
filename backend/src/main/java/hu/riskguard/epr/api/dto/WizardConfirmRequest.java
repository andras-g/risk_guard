package hu.riskguard.epr.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST /wizard/confirm} — persist calculation and link to template.
 *
 * @param configVersion          config version used for this traversal
 * @param traversalPath          full path of selections (JSONB-stored)
 * @param kfCode                 resolved 8-digit KF code (original wizard suggestion)
 * @param feeRate                fee rate in Ft/kg
 * @param materialClassification human-readable label
 * @param templateId             optional — material template to link (nullable)
 * @param confidenceScore        confidence level: "HIGH", "MEDIUM", or "LOW"
 * @param overrideKfCode         user-selected override KF-code (nullable — NULL means no override)
 * @param overrideReason         free-text reason for manual override (nullable)
 */
public record WizardConfirmRequest(
        int configVersion,
        @NotNull List<WizardSelection> traversalPath,
        @NotBlank String kfCode,
        @NotNull BigDecimal feeRate,
        @NotBlank String materialClassification,
        UUID templateId,
        @NotBlank String confidenceScore,
        String overrideKfCode,
        String overrideReason
) {}
