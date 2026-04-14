package hu.riskguard.epr.registry.classifier;

import java.util.List;

/**
 * A single KF-code suggestion produced by {@link KfCodeClassifierService}.
 *
 * @param kfCode                     8-digit KF code string
 * @param suggestedComponentDescriptions human-readable component material descriptions
 * @param score                      classifier confidence score (0.0 – 1.0)
 */
public record KfSuggestion(
        String kfCode,
        List<String> suggestedComponentDescriptions,
        double score
) {}
