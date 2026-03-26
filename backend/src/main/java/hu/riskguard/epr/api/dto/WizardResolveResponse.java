package hu.riskguard.epr.api.dto;

import hu.riskguard.epr.domain.DagEngine;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response for {@code POST /wizard/resolve} — resolved KF-code and fee rate.
 *
 * @param kfCode                 8-digit KF code (e.g., "11010101")
 * @param feeCode                4-digit díjkód (e.g., "1101")
 * @param feeRate                fee rate in Ft/kg
 * @param currency               currency code ("HUF")
 * @param materialClassification human-readable label
 * @param traversalPath          full path of selections
 * @param legislationRef         legislation reference for traceability
 * @param confidenceScore        confidence in the mapping: "HIGH", "MEDIUM", or "LOW"
 * @param confidenceReason       i18n-key-compatible reason code for the confidence level
 */
public record WizardResolveResponse(
        String kfCode,
        String feeCode,
        BigDecimal feeRate,
        String currency,
        String materialClassification,
        List<WizardSelection> traversalPath,
        String legislationRef,
        String confidenceScore,
        String confidenceReason
) {

    public static WizardResolveResponse from(DagEngine.KfCodeResolution resolution,
                                              List<WizardSelection> traversalPath) {
        return new WizardResolveResponse(
                resolution.kfCode(),
                resolution.feeCode(),
                resolution.feeRate(),
                resolution.currency(),
                resolution.classification(),
                traversalPath,
                resolution.legislationRef(),
                resolution.confidence().name(),
                resolution.confidenceReason()
        );
    }
}
