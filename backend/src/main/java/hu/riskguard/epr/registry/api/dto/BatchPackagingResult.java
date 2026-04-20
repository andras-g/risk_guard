package hu.riskguard.epr.registry.api.dto;

import hu.riskguard.epr.registry.classifier.ClassificationResult;
import hu.riskguard.epr.registry.classifier.ClassificationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One classification result inside a {@link BatchPackagingResponse} (Story 10.3).
 *
 * <p>{@code classificationStrategy} is a string (NOT the enum) to keep the public DTO
 * contract stable across future strategies. Mapping (AC #23, #24):
 * <ul>
 *   <li>{@link ClassificationStrategy#VERTEX_GEMINI} → {@code "GEMINI"}</li>
 *   <li>{@link ClassificationStrategy#VTSZ_PREFIX}  → {@code "VTSZ_PREFIX_FALLBACK"}</li>
 *   <li>{@link ClassificationStrategy#NONE}         → {@code "UNRESOLVED"}</li>
 * </ul>
 */
public record BatchPackagingResult(
        String vtsz,
        String description,
        List<PackagingLayerDto> layers,
        String classificationStrategy,
        String modelVersion
) {
    private static final Logger log = LoggerFactory.getLogger(BatchPackagingResult.class);

    public static final String STRATEGY_GEMINI = "GEMINI";
    public static final String STRATEGY_VTSZ_FALLBACK = "VTSZ_PREFIX_FALLBACK";
    public static final String STRATEGY_UNRESOLVED = "UNRESOLVED";

    private static final int MAX_LAYERS = 3;

    /**
     * Map a {@link ClassificationResult} to a per-pair response item, applying
     * T3 layer-bound filtering (AC #17), per-level deduplication, and 3-layer truncation.
     *
     * <p>Behaviour by strategy:
     * <ul>
     *   <li>{@code VERTEX_GEMINI}: layers run through {@link PackagingLayerDto#from},
     *       drop nulls, sort ascending by level, keep first occurrence per level,
     *       truncate to 3. If everything is dropped → {@code UNRESOLVED}, empty layers.</li>
     *   <li>{@code VTSZ_PREFIX}: same filter (single-layer fallback expected;
     *       null weight permitted). Mapped to {@code VTSZ_PREFIX_FALLBACK}; modelVersion null.</li>
     *   <li>{@code NONE}: maps to {@code UNRESOLVED}, empty layers.</li>
     * </ul>
     */
    public static BatchPackagingResult from(String vtsz, String description, ClassificationResult result) {
        Objects.requireNonNull(result, "result must not be null");

        if (result.strategy() == ClassificationStrategy.NONE || result.suggestions().isEmpty()) {
            return new BatchPackagingResult(vtsz, description, List.of(), STRATEGY_UNRESOLVED, null);
        }

        List<PackagingLayerDto> layers = new ArrayList<>();
        Set<Integer> seenLevels = new HashSet<>();
        result.suggestions().stream()
                .map(PackagingLayerDto::from)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(PackagingLayerDto::level))
                .forEach(layer -> {
                    if (seenLevels.add(layer.level()) && layers.size() < MAX_LAYERS) {
                        layers.add(layer);
                    }
                });

        if (layers.isEmpty()) {
            // All layers dropped by T3 bounds — promote to UNRESOLVED (AC #17 last bullet, R1-P7)
            log.warn("all {} layers T3-dropped for vtsz={}, strategy={} → UNRESOLVED",
                    result.suggestions().size(), vtsz, result.strategy());
            return new BatchPackagingResult(vtsz, description, List.of(), STRATEGY_UNRESOLVED, null);
        }

        String strategy = strategyToString(result.strategy());

        // VTSZ-prefix fallback never carries a model version (AC #23)
        String modelVersion = result.strategy() == ClassificationStrategy.VERTEX_GEMINI
                ? result.modelVersion() : null;

        return new BatchPackagingResult(vtsz, description, List.copyOf(layers), strategy, modelVersion);
    }

    // Plain if/else (not a switch expression) so javac does NOT emit a synthetic
    // BatchPackagingResult$1 holder for the enum switch table — the api.dto package
    // ArchUnit rule (`dtos_should_be_records`) rejects any non-record class living here.
    private static String strategyToString(ClassificationStrategy s) {
        if (s == ClassificationStrategy.VERTEX_GEMINI) return STRATEGY_GEMINI;
        if (s == ClassificationStrategy.VTSZ_PREFIX) return STRATEGY_VTSZ_FALLBACK;
        return STRATEGY_UNRESOLVED;
    }

    /** Convenience for per-pair failure isolation (AC #5): empty/UNRESOLVED with no model version. */
    public static BatchPackagingResult unresolved(String vtsz, String description) {
        return new BatchPackagingResult(vtsz, description, List.of(), STRATEGY_UNRESOLVED, null);
    }
}
