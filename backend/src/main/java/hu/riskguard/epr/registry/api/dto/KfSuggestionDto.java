package hu.riskguard.epr.registry.api.dto;

import hu.riskguard.epr.registry.classifier.KfSuggestion;

import java.util.List;

/**
 * DTO for a single KF code suggestion.
 */
public record KfSuggestionDto(
        String kfCode,
        List<String> suggestedComponentDescriptions,
        double score
) {
    public static KfSuggestionDto from(KfSuggestion suggestion) {
        return new KfSuggestionDto(
                suggestion.kfCode(),
                suggestion.suggestedComponentDescriptions(),
                suggestion.score()
        );
    }
}
