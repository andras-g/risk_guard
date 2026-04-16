package hu.riskguard.epr.registry.classifier;

import hu.riskguard.core.security.TenantContext;
import hu.riskguard.epr.registry.classifier.internal.VertexAiGeminiClassifier;
import hu.riskguard.epr.registry.classifier.internal.VtszPrefixFallbackClassifier;
import hu.riskguard.epr.registry.domain.ClassifierUsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Primary {@link KfCodeClassifierService} that routes between Vertex AI Gemini and
 * the VTSZ-prefix fallback, enforcing monthly cap and confidence threshold.
 *
 * <p>Routing logic (AC 4):
 * <ol>
 *   <li>If {@code ClassifierUsageService.isCapExceeded(tenantId)}: skip Gemini, go to step 3.</li>
 *   <li>Call {@code VertexAiGeminiClassifier}. If confidence &ge; threshold AND suggestions non-empty:
 *       increment usage counter and return result.</li>
 *   <li>Fall through to {@code VtszPrefixFallbackClassifier}. If non-empty: return result.</li>
 *   <li>Return {@link ClassificationResult#empty()}.</li>
 * </ol>
 *
 * <p>{@code tenantId} is obtained from {@link TenantContext#getCurrentTenant()} which is populated
 * by {@code TenantFilter} for all authenticated HTTP requests. Never added as a parameter to
 * {@link KfCodeClassifierService#classify(String, String)}.
 */
@Component
@Primary
public class ClassifierRouter implements KfCodeClassifierService {

    private static final Logger log = LoggerFactory.getLogger(ClassifierRouter.class);

    private final VertexAiGeminiClassifier geminiClassifier;
    private final VtszPrefixFallbackClassifier vtszFallback;
    private final ClassifierUsageService usageService;
    private final ClassificationConfidence confidenceThreshold;

    public ClassifierRouter(
            VertexAiGeminiClassifier geminiClassifier,
            VtszPrefixFallbackClassifier vtszFallback,
            ClassifierUsageService usageService,
            @Value("${risk-guard.classifier.confidence-threshold:MEDIUM}") String confidenceThreshold) {
        this.geminiClassifier = geminiClassifier;
        this.vtszFallback = vtszFallback;
        this.usageService = usageService;
        this.confidenceThreshold = ClassificationConfidence.valueOf(confidenceThreshold);
    }

    @Override
    public ClassificationResult classify(String productName, String vtsz) {
        UUID tenantId = TenantContext.getCurrentTenant();

        // Step 1: Check monthly cap — if exceeded, skip Gemini entirely
        if (tenantId != null && usageService.isCapExceeded(tenantId)) {
            log.debug("Gemini cap exceeded for tenant {} — falling back to VTSZ prefix", tenantId);
            return fallbackToVtsz(productName, vtsz);
        }

        // Step 2: Try Gemini
        ClassificationResult geminiResult = geminiClassifier.classify(productName, vtsz);
        if (!geminiResult.suggestions().isEmpty()
                && geminiResult.confidence().ordinal() >= confidenceThreshold.ordinal()) {
            if (tenantId != null) {
                usageService.incrementUsage(tenantId, geminiResult.inputTokens(), geminiResult.outputTokens());
            }
            return geminiResult;
        }

        // Step 3: Fall through to VTSZ prefix
        return fallbackToVtsz(productName, vtsz);
    }

    private ClassificationResult fallbackToVtsz(String productName, String vtsz) {
        ClassificationResult vtszResult = vtszFallback.classify(productName, vtsz);
        if (!vtszResult.suggestions().isEmpty()) {
            return vtszResult;
        }
        return ClassificationResult.empty();
    }
}
