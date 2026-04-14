package hu.riskguard.epr.registry.classifier;

/**
 * Port for KF-code classification of packaging products.
 *
 * <p>Implementations may be synchronous (rule-based lookup) or asynchronous
 * (Vertex AI Gemini call). Callers MUST NOT assume speed — a call may block
 * for several seconds depending on the active implementation.
 *
 * <p>Story 9.2 provides only {@code NullKfCodeClassifier} (always returns
 * {@link ClassificationResult#empty()}). Story 9.3 will introduce
 * {@code VertexAiGeminiClassifier}, {@code VtszPrefixFallbackClassifier},
 * and a {@code ClassifierRouter @Primary} bean.
 */
public interface KfCodeClassifierService {

    /**
     * Classify a packaging product by name and optional VTSZ code.
     *
     * @param productName human-readable product or line-item description
     * @param vtsz        Hungarian VTSZ tariff code (may be {@code null})
     * @return classification result; never {@code null}
     */
    ClassificationResult classify(String productName, String vtsz);
}
