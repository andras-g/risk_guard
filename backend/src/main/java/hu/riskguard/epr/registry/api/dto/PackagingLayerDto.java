package hu.riskguard.epr.registry.api.dto;

import hu.riskguard.epr.registry.classifier.KfSuggestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * One packaging layer in a {@link BatchPackagingResult} (Story 10.3).
 *
 * <p>Layer levels: 1 = primary, 2 = secondary, 3 = tertiary
 * (matches {@code products_components.wrapping_level} from Story 10.1).
 *
 * <p>{@code weightEstimateKg} is nullable: the rule-based VTSZ-prefix fallback
 * does not estimate weight (returns {@code null}), and AC #23 keeps the layer
 * regardless. When non-null, the value MUST satisfy the strict T3 bound
 * {@code (0, 10000]} — see {@link #from(KfSuggestion)}.
 */
public record PackagingLayerDto(
        int level,
        String kfCode,
        BigDecimal weightEstimateKg,
        int itemsPerParent,
        String description
) {

    private static final Logger log = LoggerFactory.getLogger(PackagingLayerDto.class);
    private static final Pattern KF_CODE_PATTERN = Pattern.compile("^\\d{8}$");
    private static final BigDecimal MAX_WEIGHT_KG = BigDecimal.valueOf(10000);
    private static final int MAX_ITEMS_PER_PARENT = 10000;

    /**
     * Factory + T3 defensive bounds (AC #17).
     *
     * <p>Returns {@code null} — caller filters nulls — when any of:
     * <ul>
     *   <li>{@code level} not in {1, 2, 3} (mapped from {@code KfSuggestion.layer}
     *       string {@code primary|secondary|tertiary}; unknown layer drops the suggestion)</li>
     *   <li>{@code kfCode} not 8 digits</li>
     *   <li>{@code itemsPerParent} not in {@code (0, 10000]}</li>
     *   <li>{@code weightEstimateKg} present and not in {@code (0, 10000]}</li>
     * </ul>
     *
     * <p>{@code weightEstimateKg == null} is permitted (rule-based fallback path).
     */
    public static PackagingLayerDto from(KfSuggestion suggestion) {
        if (suggestion == null) {
            log.warn("layer dropped: suggestion was null (defensive guard against forgiving JSON parsers)");
            return null;
        }
        int level = layerToLevel(suggestion.layer());
        if (level == 0) {
            log.warn("layer dropped: unknown layer name '{}' for kfCode={}",
                    suggestion.layer(), suggestion.kfCode());
            return null;
        }

        String kfCode = suggestion.kfCode();
        if (kfCode == null || !KF_CODE_PATTERN.matcher(kfCode).matches()) {
            log.warn("layer dropped: invalid kfCode '{}' (must match ^\\d{{8}}$)", kfCode);
            return null;
        }

        int itemsPerParent = suggestion.unitsPerProduct();
        if (itemsPerParent <= 0 || itemsPerParent > MAX_ITEMS_PER_PARENT) {
            log.warn("layer dropped: itemsPerParent {} out of (0, {}] for kfCode={}",
                    itemsPerParent, MAX_ITEMS_PER_PARENT, kfCode);
            return null;
        }

        BigDecimal weight = suggestion.weightEstimateKg();
        if (weight != null) {
            if (weight.signum() <= 0 || weight.compareTo(MAX_WEIGHT_KG) > 0) {
                log.warn("layer dropped: weight {} out of (0, {}] for kfCode={}",
                        weight, MAX_WEIGHT_KG, kfCode);
                return null;
            }
        }

        return new PackagingLayerDto(level, kfCode, weight, itemsPerParent, suggestion.description());
    }

    private static int layerToLevel(String layer) {
        if (layer == null) return 0;
        return switch (layer) {
            case "primary" -> 1;
            case "secondary" -> 2;
            case "tertiary" -> 3;
            default -> 0;
        };
    }
}
