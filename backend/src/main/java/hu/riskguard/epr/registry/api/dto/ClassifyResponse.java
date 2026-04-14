package hu.riskguard.epr.registry.api.dto;

import hu.riskguard.epr.registry.classifier.ClassificationResult;

import java.util.List;

/**
 * Response body for {@code POST /api/v1/registry/classify}.
 */
public record ClassifyResponse(
        List<KfSuggestionDto> suggestions,
        String strategy,
        String confidence,
        String modelVersion
) {
    public static ClassifyResponse from(ClassificationResult result) {
        return new ClassifyResponse(
                result.suggestions().stream().map(KfSuggestionDto::from).toList(),
                result.strategy().name(),
                result.confidence().name(),
                result.modelVersion()
        );
    }
}
