package hu.riskguard.epr.registry;

import hu.riskguard.epr.internal.EprRepository;
import hu.riskguard.epr.registry.classifier.ClassificationConfidence;
import hu.riskguard.epr.registry.classifier.ClassificationResult;
import hu.riskguard.epr.registry.classifier.ClassificationStrategy;
import hu.riskguard.epr.registry.classifier.internal.VtszPrefixFallbackClassifier;
import org.jooq.JSONB;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VtszPrefixFallbackClassifier}.
 * Verifies null/blank VTSZ early exit, no active config, and prefix matching.
 */
@ExtendWith(MockitoExtension.class)
class VtszPrefixFallbackClassifierTest {

    @Mock private EprRepository eprRepository;

    private VtszPrefixFallbackClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new VtszPrefixFallbackClassifier(eprRepository);
    }

    // ─── Test 1: null VTSZ returns empty (no DB call) ─────────────────────────

    @Test
    void classify_nullVtsz_returnsEmpty() {
        ClassificationResult result = classifier.classify("Termék A", null);

        assertThat(result.suggestions()).isEmpty();
        assertThat(result.strategy()).isEqualTo(ClassificationStrategy.NONE);
        verifyNoInteractions(eprRepository);
    }

    // ─── Test 2: blank VTSZ returns empty (no DB call) ────────────────────────

    @Test
    void classify_blankVtsz_returnsEmpty() {
        ClassificationResult result = classifier.classify("Termék A", "   ");

        assertThat(result.suggestions()).isEmpty();
        verifyNoInteractions(eprRepository);
    }

    // ─── Test 3: no active EPR config → returns empty ─────────────────────────

    @Test
    void classify_noActiveConfig_returnsEmpty() {
        when(eprRepository.findActiveConfig()).thenReturn(Optional.empty());

        ClassificationResult result = classifier.classify("Termék A", "39239090");

        assertThat(result.suggestions()).isEmpty();
    }

    // ─── Test 4: VTSZ matches longest prefix in config → returns MEDIUM result ─

    @Test
    void classify_vtszMatchesPrefix_returnsMediumConfidenceResult() {
        // Config with vtszMappings: prefix "3923" → kfCode "11010101"
        String configJson = """
                {"vtszMappings":[
                    {"vtszPrefix":"3923","kfCode":"11010101","materialName_hu":"PET"}
                ]}
                """;
        Record mockRecord = mockRecordWithJsonb(configJson);
        when(eprRepository.findActiveConfig()).thenReturn(Optional.of(mockRecord));

        ClassificationResult result = classifier.classify("PET palack", "39239090");

        assertThat(result.suggestions()).hasSize(1);
        assertThat(result.suggestions().get(0).kfCode()).isEqualTo("11010101");
        assertThat(result.confidence()).isEqualTo(ClassificationConfidence.MEDIUM);
        assertThat(result.strategy()).isEqualTo(ClassificationStrategy.VTSZ_PREFIX);
        assertThat(result.suggestions().get(0).score()).isEqualTo(0.65);
    }

    // ─── Test 5: VTSZ matches longest of multiple prefixes ────────────────────

    @Test
    void classify_vtszMatchesLongestPrefix() {
        String configJson = """
                {"vtszMappings":[
                    {"vtszPrefix":"39","kfCode":"11010101","materialName_hu":"Műanyag általános"},
                    {"vtszPrefix":"3923","kfCode":"11020101","materialName_hu":"PET specifikus"}
                ]}
                """;
        Record mockRecord = mockRecordWithJsonb(configJson);
        when(eprRepository.findActiveConfig()).thenReturn(Optional.of(mockRecord));

        ClassificationResult result = classifier.classify("PET flakon", "39239090");

        assertThat(result.suggestions()).hasSize(1);
        // Longest match "3923" wins over "39"
        assertThat(result.suggestions().get(0).kfCode()).isEqualTo("11020101");
    }

    // ─── Test 6: VTSZ has no matching prefix → returns VTSZ_PREFIX strategy, empty suggestions

    @Test
    void classify_vtszNoMatch_returnsLowConfidenceEmpty() {
        String configJson = """
                {"vtszMappings":[
                    {"vtszPrefix":"7013","kfCode":"30010101","materialName_hu":"Üveg"}
                ]}
                """;
        Record mockRecord = mockRecordWithJsonb(configJson);
        when(eprRepository.findActiveConfig()).thenReturn(Optional.of(mockRecord));

        // 3923 doesn't start with 7013
        ClassificationResult result = classifier.classify("PET palack", "39239090");

        assertThat(result.suggestions()).isEmpty();
        assertThat(result.strategy()).isEqualTo(ClassificationStrategy.VTSZ_PREFIX);
        assertThat(result.confidence()).isEqualTo(ClassificationConfidence.LOW);
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private Record mockRecordWithJsonb(String json) {
        Record record = mock(Record.class);
        when(record.get("config_data")).thenReturn(JSONB.valueOf(json));
        return record;
    }
}
