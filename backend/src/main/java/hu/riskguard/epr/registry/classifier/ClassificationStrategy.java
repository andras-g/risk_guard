package hu.riskguard.epr.registry.classifier;

/**
 * Strategy used to produce a {@link ClassificationResult}.
 * Story 9.2 ships only {@code NONE} (via {@code NullKfCodeClassifier}).
 * {@code VERTEX_GEMINI} and {@code VTSZ_PREFIX} are introduced in Story 9.3.
 */
public enum ClassificationStrategy {
    VERTEX_GEMINI,
    VTSZ_PREFIX,
    NONE
}
