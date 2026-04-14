package hu.riskguard.epr.registry.classifier;

/**
 * Confidence level of a {@link ClassificationResult}.
 *
 * <p>Ordinal order is meaningful: LOW(0) &lt; MEDIUM(1) &lt; HIGH(2).
 * {@link hu.riskguard.epr.registry.classifier.ClassifierRouter} compares ordinals
 * to enforce the confidence threshold. Do not reorder enum constants.
 */
public enum ClassificationConfidence {
    LOW,
    MEDIUM,
    HIGH
}
