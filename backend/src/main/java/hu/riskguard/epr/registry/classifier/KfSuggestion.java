package hu.riskguard.epr.registry.classifier;

import java.math.BigDecimal;

/**
 * A single KF-code suggestion produced by {@link KfCodeClassifierService}.
 *
 * <p>Story 9.6: extended for multi-layer packaging. Each suggestion now represents
 * one packaging layer (primary/secondary/tertiary) rather than an alternative KF code.
 *
 * @param kfCode           8-digit KF code string
 * @param description      human-readable material description (Hungarian)
 * @param score            classifier confidence score (0.0 – 1.0)
 * @param layer            packaging layer: "primary", "secondary", or "tertiary"
 * @param weightEstimateKg AI-estimated weight of one packaging unit in kg (nullable)
 * @param unitsPerProduct  how many product units fit in one packaging unit (1 = primary)
 */
public record KfSuggestion(
        String kfCode,
        String description,
        double score,
        String layer,
        BigDecimal weightEstimateKg,
        int unitsPerProduct
) {}
