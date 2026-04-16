package hu.riskguard.epr.registry.api.dto;

import hu.riskguard.epr.registry.classifier.KfSuggestion;

import java.math.BigDecimal;

/**
 * DTO for a single KF code suggestion (multi-layer, Story 9.6).
 */
public record KfSuggestionDto(
        String kfCode,
        String description,
        double score,
        String layer,
        BigDecimal weightEstimateKg,
        int unitsPerProduct
) {
    public static KfSuggestionDto from(KfSuggestion suggestion) {
        return new KfSuggestionDto(
                suggestion.kfCode(),
                suggestion.description(),
                suggestion.score(),
                suggestion.layer(),
                suggestion.weightEstimateKg(),
                suggestion.unitsPerProduct()
        );
    }
}
