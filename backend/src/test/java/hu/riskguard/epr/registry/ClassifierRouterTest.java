package hu.riskguard.epr.registry;

import hu.riskguard.core.security.TenantContext;
import hu.riskguard.epr.registry.classifier.*;
import hu.riskguard.epr.registry.classifier.internal.VertexAiGeminiClassifier;
import hu.riskguard.epr.registry.classifier.internal.VtszPrefixFallbackClassifier;
import hu.riskguard.epr.registry.domain.ClassifierUsageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ClassifierRouter}.
 * Verifies routing logic: cap enforcement, confidence threshold, fallback, usage increment.
 */
@ExtendWith(MockitoExtension.class)
class ClassifierRouterTest {

    @Mock private VertexAiGeminiClassifier geminiClassifier;
    @Mock private VtszPrefixFallbackClassifier vtszFallback;
    @Mock private ClassifierUsageService usageService;

    private ClassifierRouter router;

    private static final UUID TENANT_ID = UUID.randomUUID();

    /** A HIGH-confidence Gemini result with one suggestion. */
    private static final ClassificationResult GEMINI_HIGH = new ClassificationResult(
            List.of(new KfSuggestion("11010101", "PET", 0.90, "primary", null, 1)),
            ClassificationStrategy.VERTEX_GEMINI,
            ClassificationConfidence.HIGH,
            "gemini-3.0-flash-preview",
            Instant.now(),
            120, 45
    );

    /** A MEDIUM-confidence Gemini result with one suggestion. */
    private static final ClassificationResult GEMINI_MEDIUM = new ClassificationResult(
            List.of(new KfSuggestion("11020101", "HDPE", 0.65, "primary", null, 1)),
            ClassificationStrategy.VERTEX_GEMINI,
            ClassificationConfidence.MEDIUM,
            "gemini-3.0-flash-preview",
            Instant.now(),
            115, 40
    );

    /** A LOW-confidence Gemini result with one suggestion. */
    private static final ClassificationResult GEMINI_LOW = new ClassificationResult(
            List.of(new KfSuggestion("11030101", "PVC", 0.30, "primary", null, 1)),
            ClassificationStrategy.VERTEX_GEMINI,
            ClassificationConfidence.LOW,
            "gemini-3.0-flash-preview",
            Instant.now(),
            110, 38
    );

    /** A VTSZ-prefix result with one suggestion. */
    private static final ClassificationResult VTSZ_RESULT = new ClassificationResult(
            List.of(new KfSuggestion("30010101", "Üveg", 0.65, "primary", null, 1)),
            ClassificationStrategy.VTSZ_PREFIX,
            ClassificationConfidence.MEDIUM,
            null,
            Instant.now(),
            0, 0
    );

    @BeforeEach
    void setUp() {
        // Default threshold: MEDIUM
        router = new ClassifierRouter(geminiClassifier, vtszFallback, usageService, "MEDIUM");
        TenantContext.setCurrentTenant(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── Test 1: Gemini HIGH confidence returns Gemini result ─────────────────

    @Test
    void route_gemini_high_confidence_returns_gemini_result() {
        when(usageService.isCapExceeded(TENANT_ID)).thenReturn(false);
        when(geminiClassifier.classify(anyString(), anyString())).thenReturn(GEMINI_HIGH);

        ClassificationResult result = router.classify("PET palack", "39239090");

        assertThat(result.strategy()).isEqualTo(ClassificationStrategy.VERTEX_GEMINI);
        assertThat(result.confidence()).isEqualTo(ClassificationConfidence.HIGH);
        assertThat(result.suggestions()).hasSize(1);
        assertThat(result.suggestions().get(0).kfCode()).isEqualTo("11010101");
    }

    // ─── Test 2: Gemini MEDIUM confidence (= threshold) returns Gemini result ─

    @Test
    void route_gemini_medium_confidence_returns_gemini_result() {
        when(usageService.isCapExceeded(TENANT_ID)).thenReturn(false);
        when(geminiClassifier.classify(anyString(), anyString())).thenReturn(GEMINI_MEDIUM);

        ClassificationResult result = router.classify("HDPE flakon", "39239090");

        assertThat(result.strategy()).isEqualTo(ClassificationStrategy.VERTEX_GEMINI);
        assertThat(result.confidence()).isEqualTo(ClassificationConfidence.MEDIUM);
    }

    // ─── Test 3: Gemini LOW confidence falls through to VTSZ ──────────────────

    @Test
    void route_gemini_low_confidence_falls_through_to_vtsz() {
        when(usageService.isCapExceeded(TENANT_ID)).thenReturn(false);
        when(geminiClassifier.classify(anyString(), anyString())).thenReturn(GEMINI_LOW);
        when(vtszFallback.classify(anyString(), anyString())).thenReturn(VTSZ_RESULT);

        ClassificationResult result = router.classify("Ismeretlen anyag", "39239090");

        assertThat(result.strategy()).isEqualTo(ClassificationStrategy.VTSZ_PREFIX);
        verify(vtszFallback).classify(anyString(), anyString());
    }

    // ─── Test 4: Gemini returns empty → falls through to VTSZ ─────────────────

    @Test
    void route_gemini_returns_empty_falls_through_to_vtsz() {
        when(usageService.isCapExceeded(TENANT_ID)).thenReturn(false);
        when(geminiClassifier.classify(anyString(), anyString())).thenReturn(ClassificationResult.empty());
        when(vtszFallback.classify(anyString(), anyString())).thenReturn(VTSZ_RESULT);

        ClassificationResult result = router.classify("Termék A", "12345678");

        assertThat(result.strategy()).isEqualTo(ClassificationStrategy.VTSZ_PREFIX);
        verify(vtszFallback).classify(anyString(), anyString());
    }

    // ─── Test 5: cap exceeded — skips Gemini, uses VTSZ ──────────────────────

    @Test
    void route_cap_exceeded_skips_gemini_uses_vtsz() {
        when(usageService.isCapExceeded(TENANT_ID)).thenReturn(true);
        when(vtszFallback.classify(anyString(), anyString())).thenReturn(VTSZ_RESULT);

        ClassificationResult result = router.classify("Termék B", "39239090");

        assertThat(result.strategy()).isEqualTo(ClassificationStrategy.VTSZ_PREFIX);
        verifyNoInteractions(geminiClassifier);
    }

    // ─── Test 6: cap exceeded and VTSZ also empty → returns empty ─────────────

    @Test
    void route_cap_exceeded_and_vtsz_empty_returns_empty() {
        when(usageService.isCapExceeded(TENANT_ID)).thenReturn(true);
        when(vtszFallback.classify(anyString(), any())).thenReturn(ClassificationResult.empty());

        ClassificationResult result = router.classify("Termék C", null);

        assertThat(result.suggestions()).isEmpty();
        assertThat(result.strategy()).isEqualTo(ClassificationStrategy.NONE);
        verifyNoInteractions(geminiClassifier);
    }

    // ─── Test 7: Gemini success increments usage counter ─────────────────────

    @Test
    void route_gemini_success_increments_usage_counter() {
        when(usageService.isCapExceeded(TENANT_ID)).thenReturn(false);
        when(geminiClassifier.classify(anyString(), anyString())).thenReturn(GEMINI_HIGH);

        router.classify("PET palack", "39239090");

        verify(usageService).incrementUsage(TENANT_ID, 120, 45);
    }

    // ─── Test 8: VTSZ fallback does NOT increment usage counter ───────────────

    @Test
    void route_vtsz_fallback_does_not_increment_usage_counter() {
        when(usageService.isCapExceeded(TENANT_ID)).thenReturn(false);
        when(geminiClassifier.classify(anyString(), anyString())).thenReturn(ClassificationResult.empty());
        when(vtszFallback.classify(anyString(), anyString())).thenReturn(VTSZ_RESULT);

        router.classify("Termék", "39239090");

        verify(usageService, never()).incrementUsage(any(), anyInt(), anyInt());
    }

    // ─── Test 9: no tenant in context — cap check skipped, no increment ───────

    @Test
    void route_no_tenant_in_context_still_calls_gemini() {
        TenantContext.clear();
        when(geminiClassifier.classify(anyString(), anyString())).thenReturn(GEMINI_HIGH);

        ClassificationResult result = router.classify("PET palack", "39239090");

        assertThat(result.strategy()).isEqualTo(ClassificationStrategy.VERTEX_GEMINI);
        // isCapExceeded should NOT be called (tenantId is null)
        verify(usageService, never()).isCapExceeded(any());
        // incrementUsage should NOT be called (tenantId is null)
        verify(usageService, never()).incrementUsage(any(), anyInt(), anyInt());
    }
}
