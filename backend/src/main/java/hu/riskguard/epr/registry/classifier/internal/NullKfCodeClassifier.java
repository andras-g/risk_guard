package hu.riskguard.epr.registry.classifier.internal;

import hu.riskguard.epr.registry.classifier.ClassificationResult;
import hu.riskguard.epr.registry.classifier.KfCodeClassifierService;
import org.springframework.stereotype.Component;

/**
 * No-op classifier — always returns {@link ClassificationResult#empty()}.
 *
 * <p>This is the only {@link KfCodeClassifierService} implementation shipped in
 * Story 9.2. Story 9.3 will add {@code VertexAiGeminiClassifier} and
 * {@code VtszPrefixFallbackClassifier}, and will introduce a
 * {@code ClassifierRouter @Primary} bean that replaces this stub.
 */
@Component
public class NullKfCodeClassifier implements KfCodeClassifierService {

    @Override
    public ClassificationResult classify(String productName, String vtsz) {
        return ClassificationResult.empty();
    }
}
