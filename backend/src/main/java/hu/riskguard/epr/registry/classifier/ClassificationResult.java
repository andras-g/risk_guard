package hu.riskguard.epr.registry.classifier;

import java.time.Instant;
import java.util.List;

/**
 * Result returned by {@link KfCodeClassifierService#classify(String, String)}.
 *
 * @param suggestions       ordered list of KF-code suggestions (best first)
 * @param strategy          which classification strategy produced this result
 * @param confidence        overall confidence level
 * @param modelVersion      version string of the model used (null for rule-based or no-op strategies)
 * @param timestamp         when the classification was performed
 */
public record ClassificationResult(
        List<KfSuggestion> suggestions,
        ClassificationStrategy strategy,
        ClassificationConfidence confidence,
        String modelVersion,
        Instant timestamp
) {

    /**
     * Factory for a no-op result — used by {@code NullKfCodeClassifier} and as a
     * safe default when classification is unavailable.
     */
    public static ClassificationResult empty() {
        return new ClassificationResult(
                List.of(),
                ClassificationStrategy.NONE,
                ClassificationConfidence.LOW,
                null,
                Instant.now()
        );
    }
}
