package hu.riskguard.epr.registry.classifier.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.riskguard.epr.internal.EprRepository;
import hu.riskguard.epr.registry.classifier.ClassificationConfidence;
import hu.riskguard.epr.registry.classifier.ClassificationResult;
import hu.riskguard.epr.registry.classifier.ClassificationStrategy;
import hu.riskguard.epr.registry.classifier.KfCodeClassifierService;
import hu.riskguard.epr.registry.classifier.KfSuggestion;
import org.jooq.JSONB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rule-based KF-code classifier using VTSZ prefix matching against the active EPR config.
 *
 * <p>Replicates {@code EprService.loadVtszMappings()} + longest-prefix match logic.
 * The original in {@code EprService} is intentionally NOT removed — the quarterly
 * EPR auto-fill pipeline (Story 9.4) uses it independently.
 *
 * <p>Pure in-process: no external calls, no network dependency.
 */
@Component
public class VtszPrefixFallbackClassifier implements KfCodeClassifierService {

    private static final Logger log = LoggerFactory.getLogger(VtszPrefixFallbackClassifier.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EprRepository eprRepository;

    public VtszPrefixFallbackClassifier(EprRepository eprRepository) {
        this.eprRepository = eprRepository;
    }

    @Override
    public ClassificationResult classify(String productName, String vtsz) {
        if (vtsz == null || vtsz.isBlank()) {
            return ClassificationResult.empty();
        }

        List<VtszMapping> vtszMappings = loadVtszMappings();
        if (vtszMappings.isEmpty()) {
            return ClassificationResult.empty();
        }

        VtszMapping bestMapping = vtszMappings.stream()
                .filter(m -> vtsz.startsWith(m.vtszPrefix()))
                .max(Comparator.comparingInt(m -> m.vtszPrefix().length()))
                .orElse(null);

        if (bestMapping == null) {
            return new ClassificationResult(
                    List.of(),
                    ClassificationStrategy.VTSZ_PREFIX,
                    ClassificationConfidence.LOW,
                    null,
                    java.time.Instant.now(),
                    0, 0
            );
        }

        KfSuggestion suggestion = new KfSuggestion(
                bestMapping.kfCode(),
                bestMapping.materialName_hu(),
                0.65, // MEDIUM-confidence rule-based match
                "primary",
                null, // no weight estimate from rule-based classifier
                1
        );

        return new ClassificationResult(
                List.of(suggestion),
                ClassificationStrategy.VTSZ_PREFIX,
                ClassificationConfidence.MEDIUM,
                null,
                java.time.Instant.now(),
                0, 0
        );
    }

    private List<VtszMapping> loadVtszMappings() {
        try {
            var activeRecord = eprRepository.findActiveConfig().orElse(null);
            if (activeRecord == null) return List.of();
            Object configDataObj = activeRecord.get("config_data");
            JsonNode configNode;
            if (configDataObj instanceof JSONB jsonb) {
                configNode = OBJECT_MAPPER.readTree(jsonb.data());
            } else if (configDataObj != null) {
                configNode = OBJECT_MAPPER.readTree(configDataObj.toString());
            } else {
                return List.of();
            }
            JsonNode mappingsNode = configNode.get("vtszMappings");
            if (mappingsNode == null || !mappingsNode.isArray()) return List.of();

            List<VtszMapping> result = new ArrayList<>();
            for (JsonNode entry : mappingsNode) {
                String prefix = entry.path("vtszPrefix").asText(null);
                String kfCode = entry.path("kfCode").asText(null);
                String nameHu = entry.path("materialName_hu").asText(null);
                if (prefix != null && kfCode != null) {
                    result.add(new VtszMapping(prefix, kfCode, nameHu != null ? nameHu : prefix));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("VtszPrefixFallbackClassifier: failed to load vtszMappings: {}", e.getMessage());
            return List.of();
        }
    }

    private record VtszMapping(String vtszPrefix, String kfCode, String materialName_hu) {}
}
