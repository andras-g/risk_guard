package hu.riskguard.epr.domain;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Pure-function service that traverses the KF-code hierarchy JSON from {@code epr_configs.config_data}.
 *
 * <p>The DagEngine is stateless — it takes {@code (configData, traversalInputs, locale)} and
 * returns results. No database calls, no side effects, no injected dependencies.
 * This makes it trivially unit-testable with static JSON fixtures.
 *
 * <p>KF-code structure: {@code [product_stream 2d][material_stream 2d][group 2d][subgroup 2d]} = 8 digits.
 *
 * @see <a href="https://njt.hu/jogszabaly/2023-80-20-22">80/2023 (III. 14.) Korm. rendelet</a>
 */
@Component
public class DagEngine {

    // ─── Product stream family mapping ───────────────────────────────────────
    // Maps numeric product stream codes to the JSON section keys in the seed data.

    private static final Map<String, String> PRODUCT_STREAM_TO_FAMILY = Map.ofEntries(
            Map.entry("11", "packaging"),
            Map.entry("12", "packaging"),
            Map.entry("13", "packaging"),
            Map.entry("21", "eee"),
            Map.entry("22", "eee"),
            Map.entry("23", "eee"),
            Map.entry("24", "eee"),
            Map.entry("25", "eee"),
            Map.entry("26", "eee"),
            Map.entry("31", "batteries"),
            Map.entry("32", "batteries"),
            Map.entry("33", "batteries"),
            Map.entry("41", "tires"),
            Map.entry("51", "vehicles"),
            Map.entry("61", "office_paper"),
            Map.entry("71", "advertising_paper"),
            Map.entry("81", "single_use_plastic"),
            Map.entry("91", "other_plastic_chemical")
    );

    // Maps product stream codes to their packaging sub-type section key for groups
    private static final Map<String, String> PACKAGING_GROUP_SECTION = Map.of(
            "11", "packaging_non_deposit",
            "12", "packaging_mandatory_deposit",
            "13", "packaging_reusable"
    );

    // EEE category mapping: product stream → subgroups section key
    private static final Map<String, String> EEE_CATEGORY_MAP = Map.of(
            "21", "cat_1_large_household",
            "22", "cat_2_small_household",
            "23", "cat_3_it_telecom",
            "24", "cat_4_consumer_electronics",
            "25", "cat_5_lighting",
            "26", "cat_6_tools_other"
    );

    // Battery type mapping: product stream → subgroups section key
    private static final Map<String, String> BATTERY_TYPE_MAP = Map.of(
            "31", "portable",
            "32", "industrial",
            "33", "automotive"
    );

    // Fee rate section keys per product stream family
    private static final Map<String, String> FEE_SECTION_MAP = Map.ofEntries(
            Map.entry("11", "packaging_non_deposit"),
            Map.entry("12", "packaging_mandatory_deposit"),
            Map.entry("13", "packaging_reusable"),
            Map.entry("21", "eee"),
            Map.entry("22", "eee"),
            Map.entry("23", "eee"),
            Map.entry("24", "eee"),
            Map.entry("25", "eee"),
            Map.entry("26", "eee"),
            Map.entry("31", "batteries"),
            Map.entry("32", "batteries"),
            Map.entry("33", "batteries"),
            Map.entry("41", "tires"),
            Map.entry("51", "vehicles"),
            Map.entry("61", "office_paper"),
            Map.entry("71", "advertising_paper"),
            Map.entry("81", "single_use_plastic"),
            Map.entry("91", "other_plastic_chemical")
    );

    // Single-path product streams where material_stream, group, and subgroup are all "01"
    private static final Set<String> SINGLE_PATH_STREAMS = Set.of("51", "61", "71");

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Level 1: Returns root-level product stream options.
     */
    public List<WizardOption> getProductStreams(JsonNode configData, String locale) {
        JsonNode productStreams = configData.at("/kf_code_structure/product_streams");
        validateNode(productStreams, "product_streams");

        List<WizardOption> options = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = productStreams.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String code = entry.getKey();
            if (code.startsWith("_")) continue; // skip metadata fields like _section
            JsonNode node = entry.getValue();
            options.add(new WizardOption(
                    code,
                    localizedName(node, locale),
                    null
            ));
        }
        // Sort by code for deterministic order
        options.sort(Comparator.comparing(WizardOption::code));
        return options;
    }

    /**
     * Level 2: Returns material stream options for the selected product stream.
     * Handles packaging (enumerated), single-use plastic (enumerated), and
     * ref-only sections (auto-derived from fee_rates or auto-select "01").
     */
    public WizardStepResult getMaterialStreams(JsonNode configData, String productStreamCode, String locale) {
        validateProductStream(productStreamCode);
        String family = PRODUCT_STREAM_TO_FAMILY.get(productStreamCode);

        // Single-path streams: auto-select "01" for all remaining levels
        if (SINGLE_PATH_STREAMS.contains(productStreamCode)) {
            WizardOption autoOption = new WizardOption("01",
                    getAutoLabel(configData, family, "material_streams", locale), null);
            return new WizardStepResult(List.of(autoOption), true, autoOption);
        }

        JsonNode materialStreams = configData.at("/kf_code_structure/material_streams/" + family);
        validateNode(materialStreams, "material_streams/" + family);

        // Check if this is a ref-only section (has _ref but no enumerated children)
        if (isRefOnly(materialStreams)) {
            return handleRefOnlyMaterialStreams(configData, productStreamCode, family, locale);
        }

        // For packaging streams, use fee-rate-aligned expansion (01-11 instead of 01-07)
        // This ensures wizard selection code = fee code suffix directly
        if (family.equals("packaging")) {
            List<WizardOption> options = expandPackagingMaterialOptions(configData, productStreamCode, locale);
            return new WizardStepResult(options, false, null);
        }

        // Other enumerated sections (single_use_plastic)
        List<WizardOption> options = extractOptions(materialStreams, locale);
        boolean autoSelect = options.size() == 1;
        return new WizardStepResult(options, autoSelect, autoSelect ? options.getFirst() : null);
    }

    /**
     * Level 3: Returns group options for the selected product+material stream context.
     */
    public WizardStepResult getGroups(JsonNode configData, String productStreamCode,
                                       String materialStreamCode, String locale) {
        validateProductStream(productStreamCode);
        String family = PRODUCT_STREAM_TO_FAMILY.get(productStreamCode);

        // Single-path: auto-select "01"
        if (SINGLE_PATH_STREAMS.contains(productStreamCode)) {
            WizardOption autoOption = new WizardOption("01",
                    getAutoLabel(configData, family, "groups", locale), null);
            return new WizardStepResult(List.of(autoOption), true, autoOption);
        }

        // EEE: group is always "01" — auto-select
        if (family.equals("eee")) {
            JsonNode eeeGroups = configData.at("/kf_code_structure/groups/eee");
            String label = (eeeGroups != null && !eeeGroups.isMissingNode() && eeeGroups.has("_ref"))
                    ? eeeGroups.get("_ref").asText()
                    : localizedDefault("Default", "Alapértelmezett", locale);
            WizardOption autoOption = new WizardOption("01", label, null);
            return new WizardStepResult(List.of(autoOption), true, autoOption);
        }

        // Batteries: group is always "01" — auto-select
        if (family.equals("batteries")) {
            WizardOption autoOption = new WizardOption("01",
                    localizedDefault("Default", "Alapértelmezett", locale), null);
            return new WizardStepResult(List.of(autoOption), true, autoOption);
        }

        // Tires: have real group options (01-06)
        if (family.equals("tires")) {
            JsonNode tiresGroups = configData.at("/kf_code_structure/groups/tires");
            validateNode(tiresGroups, "groups/tires");
            List<WizardOption> options = extractOptions(tiresGroups, locale);
            return new WizardStepResult(options, options.size() == 1,
                    options.size() == 1 ? options.getFirst() : null);
        }

        // Other plastic/chemical: group is always "01"
        if (family.equals("other_plastic_chemical") || family.equals("single_use_plastic")) {
            WizardOption autoOption = new WizardOption("01",
                    localizedDefault("Default", "Alapértelmezett", locale), null);
            return new WizardStepResult(List.of(autoOption), true, autoOption);
        }

        // Packaging: look up group section by product stream
        String groupSection = PACKAGING_GROUP_SECTION.get(productStreamCode);
        if (groupSection != null) {
            JsonNode groups = configData.at("/kf_code_structure/groups/" + groupSection);
            validateNode(groups, "groups/" + groupSection);
            List<WizardOption> options = extractOptions(groups, locale);
            return new WizardStepResult(options, options.size() == 1,
                    options.size() == 1 ? options.getFirst() : null);
        }

        // Vehicles group (specific section exists)
        if (family.equals("vehicles")) {
            JsonNode vehicleGroups = configData.at("/kf_code_structure/groups/vehicles");
            validateNode(vehicleGroups, "groups/vehicles");
            List<WizardOption> options = extractOptions(vehicleGroups, locale);
            return new WizardStepResult(options, options.size() == 1,
                    options.size() == 1 ? options.getFirst() : null);
        }

        throw new IllegalArgumentException("Unsupported product stream for group lookup: " + productStreamCode);
    }

    /**
     * Level 4: Returns subgroup options for the selected product+material+group context.
     */
    public WizardStepResult getSubgroups(JsonNode configData, String productStreamCode,
                                          String materialStreamCode, String groupCode, String locale) {
        validateProductStream(productStreamCode);
        String family = PRODUCT_STREAM_TO_FAMILY.get(productStreamCode);

        // Single-path: auto-select "01"
        if (SINGLE_PATH_STREAMS.contains(productStreamCode)) {
            WizardOption autoOption = new WizardOption("01",
                    getAutoLabel(configData, family, "subgroups", locale), null);
            return new WizardStepResult(List.of(autoOption), true, autoOption);
        }

        // Look up subgroup section based on family
        JsonNode subgroups;
        switch (family) {
            case "packaging" -> {
                // Packaging non-deposit (11) always has subgroup "01"
                // Packaging mandatory-deposit (12) has rich subgroups
                // Packaging reusable (13) uses non-deposit subgroups (always "01")
                String subgroupSection = productStreamCode.equals("12")
                        ? "packaging_mandatory_deposit"
                        : "packaging_non_deposit";
                subgroups = configData.at("/kf_code_structure/subgroups/" + subgroupSection);
            }
            case "eee" -> {
                String eeeCategory = EEE_CATEGORY_MAP.get(productStreamCode);
                subgroups = configData.at("/kf_code_structure/subgroups/eee/" + eeeCategory);
            }
            case "batteries" -> {
                String batteryType = BATTERY_TYPE_MAP.get(productStreamCode);
                subgroups = configData.at("/kf_code_structure/subgroups/batteries/" + batteryType);
            }
            case "tires" -> {
                subgroups = configData.at("/kf_code_structure/subgroups/tires");
            }
            case "single_use_plastic" -> {
                subgroups = configData.at("/kf_code_structure/subgroups/single_use_plastic");
            }
            case "other_plastic_chemical" -> {
                subgroups = configData.at("/kf_code_structure/subgroups/other_plastic_chemical");
            }
            default -> {
                subgroups = configData.at("/kf_code_structure/subgroups/" + family);
            }
        }

        validateNode(subgroups, "subgroups for " + family);
        List<WizardOption> options = extractOptions(subgroups, locale);
        boolean autoSelect = options.size() == 1;
        return new WizardStepResult(options, autoSelect, autoSelect ? options.getFirst() : null);
    }

    /**
     * Resolves the final KF-code from a completed 4-level traversal.
     * Concatenates 4 two-digit codes into 8-digit KF code, derives the 4-digit díjkód,
     * and looks up the fee rate from {@code fee_rates_2026}.
     *
     * @param locale user locale for localized classification label ("hu" or "en"); defaults to "hu"
     */
    public KfCodeResolution resolveKfCode(JsonNode configData, String productStream,
                                           String materialStream, String group, String subgroup,
                                           String locale) {
        validateCode(productStream, "product_stream");
        validateCode(materialStream, "material_stream");
        validateCode(group, "group");
        validateCode(subgroup, "subgroup");

        String kfCode = productStream + materialStream + group + subgroup;
        String feeCode = productStream + materialStream;

        // Look up fee rate
        String feeSection = FEE_SECTION_MAP.get(productStream);
        if (feeSection == null) {
            throw new IllegalArgumentException("Unknown product stream for fee lookup: " + productStream);
        }

        JsonNode feeRates = configData.at("/fee_rates_2026/" + feeSection);
        if (feeRates == null || feeRates.isMissingNode()) {
            throw new IllegalArgumentException("Fee rate section not found: " + feeSection);
        }

        JsonNode feeEntry = feeRates.get(feeCode);
        if (feeEntry == null) {
            throw new IllegalArgumentException("Fee rate not found for díjkód: " + feeCode);
        }

        BigDecimal feeRate = new BigDecimal(feeEntry.get("fee_huf_per_kg").asText());
        // Use locale-aware label so English users see English classification in the result card
        String effectiveLocale = (locale != null && !locale.isBlank()) ? locale : "hu";
        String classification = localizedName(feeEntry, effectiveLocale);

        // Append group label when the product stream has multiple groups (e.g., packaging)
        // so that KF codes like 11010101 / 11010201 / 11010301 are distinguishable
        String groupLabel = resolveGroupLabel(configData, productStream, group, effectiveLocale);
        if (groupLabel != null) {
            classification = classification + " — " + groupLabel;
        }

        String legislationRef = "33/2025. (XI. 28.) EM rendelet 1. melléklet";

        // Compute confidence score based on traversal path properties
        ConfidenceResult confidenceResult = computeConfidence(configData, productStream, materialStream, group, subgroup);

        return new KfCodeResolution(kfCode, feeCode, feeRate, "HUF", classification, legislationRef,
                confidenceResult.confidence(), confidenceResult.reason());
    }

    /**
     * Convenience overload that defaults to Hungarian locale.
     * Preserves backward compatibility for callers that do not pass a locale.
     */
    public KfCodeResolution resolveKfCode(JsonNode configData, String productStream,
                                           String materialStream, String group, String subgroup) {
        return resolveKfCode(configData, productStream, materialStream, group, subgroup, "hu");
    }

    /**
     * Enumerates ALL valid leaf-node KF-codes from the config hierarchy.
     * Recursively walks all 4 levels (product stream → material stream → group → subgroup)
     * and collects every valid 8-digit KF-code with its label and fee rate.
     *
     * @param configData the parsed config JSON
     * @param locale     user locale for label localization ("hu" or "en")
     * @return flat list of all valid KF-code entries, sorted by code numerically
     */
    public List<KfCodeEntry> enumerateAllKfCodes(JsonNode configData, String locale) {
        List<KfCodeEntry> entries = new ArrayList<>();
        List<WizardOption> productStreams = getProductStreams(configData, locale);

        for (WizardOption ps : productStreams) {
            try {
                WizardStepResult msResult = getMaterialStreams(configData, ps.code(), locale);
                for (WizardOption ms : msResult.options()) {
                    try {
                        WizardStepResult gResult = getGroups(configData, ps.code(), ms.code(), locale);
                        for (WizardOption g : gResult.options()) {
                            try {
                                WizardStepResult sgResult = getSubgroups(configData, ps.code(), ms.code(), g.code(), locale);
                                for (WizardOption sg : sgResult.options()) {
                                    try {
                                        KfCodeResolution resolution = resolveKfCode(
                                                configData, ps.code(), ms.code(), g.code(), sg.code(), locale);
                                        entries.add(new KfCodeEntry(
                                                resolution.kfCode(),
                                                resolution.feeCode(),
                                                resolution.feeRate(),
                                                resolution.currency(),
                                                resolution.classification(),
                                                ps.label()
                                        ));
                                    } catch (Exception ignored) { /* skip invalid leaf combos */ }
                                }
                            } catch (Exception ignored) { /* skip invalid subgroup combos */ }
                        }
                    } catch (Exception ignored) { /* skip invalid group combos */ }
                }
            } catch (Exception ignored) { /* skip invalid material stream combos */ }
        }

        // Sort by KF-code numerically
        entries.sort(Comparator.comparing(KfCodeEntry::kfCode));
        return entries;
    }

    // ─── Internal result types ───────────────────────────────────────────────

    public enum Confidence { HIGH, MEDIUM, LOW }

    public record WizardOption(String code, String label, String description) {}

    public record WizardStepResult(List<WizardOption> options, boolean autoSelect, WizardOption autoSelectedOption) {}

    public record KfCodeResolution(String kfCode, String feeCode, BigDecimal feeRate,
                                     String currency, String classification, String legislationRef,
                                     Confidence confidence, String confidenceReason) {}

    public record KfCodeEntry(String kfCode, String feeCode, BigDecimal feeRate,
                               String currency, String classification, String productStreamLabel) {}

    private record ConfidenceResult(Confidence confidence, String reason) {}

    // ─── Ref-only product stream families ────────────────────────────────────
    // These families have _ref-based sections with limited branching and auto-selected levels.
    private static final Set<String> REF_ONLY_FAMILIES = Set.of(
            "eee", "batteries", "tires", "vehicles", "office_paper", "advertising_paper"
    );

    // Composite material stream codes (expanded from original "07 Composite")
    private static final Set<String> COMPOSITE_MATERIAL_CODES = Set.of("08", "09", "10", "11");

    /**
     * Computes the confidence score for a resolved KF-code based on traversal path properties.
     * This is a pure function — no database calls, no side effects.
     *
     * <p>Confidence rules:
     * <ul>
     *   <li><b>LOW:</b> Composite materials (08-11), catch-all "99" subgroups, product stream 91</li>
     *   <li><b>MEDIUM:</b> Ref-only families (EEE, batteries, tires, vehicles, paper), reusable packaging (13),
     *       2+ auto-selected levels</li>
     *   <li><b>HIGH:</b> Standard packaging (11, 12) with explicit selections, single-use plastic (81)</li>
     * </ul>
     */
    private ConfidenceResult computeConfidence(JsonNode configData, String productStream,
                                                String materialStream, String group, String subgroup) {
        // LOW: composite material codes (08-11 in packaging)
        if (COMPOSITE_MATERIAL_CODES.contains(materialStream)
                && Set.of("11", "12", "13").contains(productStream)) {
            return new ConfidenceResult(Confidence.LOW, "composite_material");
        }

        // LOW: catch-all "99" subgroups
        if ("99".equals(subgroup)) {
            return new ConfidenceResult(Confidence.LOW, "catchall_category");
        }

        // LOW: product stream 91 (other plastic/chemical — broad category)
        if ("91".equals(productStream)) {
            return new ConfidenceResult(Confidence.LOW, "catchall_category");
        }

        // MEDIUM: ref-only families (EEE, batteries, tires, vehicles, office paper, advertising paper)
        String family = PRODUCT_STREAM_TO_FAMILY.get(productStream);
        if (family != null && REF_ONLY_FAMILIES.contains(family)) {
            return new ConfidenceResult(Confidence.MEDIUM, "ref_only_section");
        }

        // MEDIUM: reusable packaging (13) — less common packaging type, higher classification ambiguity
        if ("13".equals(productStream)) {
            return new ConfidenceResult(Confidence.MEDIUM, "reusable_packaging");
        }

        // MEDIUM: count auto-selectable levels — if 2+ levels had only 1 option
        int autoSelectCount = countAutoSelectableLevels(configData, productStream, materialStream, group);
        if (autoSelectCount >= 2) {
            return new ConfidenceResult(Confidence.MEDIUM, "ref_only_section");
        }

        // HIGH: standard packaging (11, 12) with explicit selections, single-use plastic (81)
        return new ConfidenceResult(Confidence.HIGH, "full_traversal");
    }

    /**
     * Counts how many intermediate levels (material_stream, group, subgroup) would have been
     * auto-selected because only a single option exists at that level.
     */
    private int countAutoSelectableLevels(JsonNode configData, String productStream,
                                           String materialStream, String group) {
        int count = 0;
        String locale = "hu"; // locale doesn't matter for counting options

        // Check material_stream level
        try {
            WizardStepResult msResult = getMaterialStreams(configData, productStream, locale);
            if (msResult.autoSelect()) count++;
        } catch (Exception ignored) { /* defensive */ }

        // Check group level
        try {
            WizardStepResult gResult = getGroups(configData, productStream, materialStream, locale);
            if (gResult.autoSelect()) count++;
        } catch (Exception ignored) { /* defensive */ }

        // Check subgroup level
        try {
            WizardStepResult sgResult = getSubgroups(configData, productStream, materialStream, group, locale);
            if (sgResult.autoSelect()) count++;
        } catch (Exception ignored) { /* defensive */ }

        return count;
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void validateProductStream(String code) {
        if (code == null || !PRODUCT_STREAM_TO_FAMILY.containsKey(code)) {
            throw new IllegalArgumentException("Invalid product stream code: " + code);
        }
    }

    private void validateCode(String code, String fieldName) {
        // Codes must be exactly 2 numeric digits (e.g., "01", "11") to produce a valid 8-digit KF code.
        // Accepting 1-digit codes would produce malformed 6- or 7-character KF codes.
        if (code == null || !code.matches("\\d{2}")) {
            throw new IllegalArgumentException("Invalid " + fieldName + " code (must be exactly 2 digits): " + code);
        }
    }

    private void validateNode(JsonNode node, String path) {
        if (node == null || node.isMissingNode()) {
            throw new IllegalArgumentException("Config data missing section: " + path);
        }
    }

    /**
     * Resolves the group label for a KF-code's product stream + group code.
     * Returns null when the group is auto-selected "01" (single option) — no need to distinguish.
     * Returns the label when there are multiple groups (e.g., packaging: Fogyasztói/Gyűjtő/Szállítási).
     */
    private String resolveGroupLabel(JsonNode configData, String productStream, String group, String locale) {
        String groupSection = PACKAGING_GROUP_SECTION.get(productStream);
        if (groupSection != null) {
            JsonNode groups = configData.at("/kf_code_structure/groups/" + groupSection);
            if (groups != null && !groups.isMissingNode()) {
                JsonNode groupNode = groups.get(group);
                if (groupNode != null && !groupNode.isMissingNode()) {
                    // Only append if there are multiple groups (not just a single auto-selected one)
                    long count = 0;
                    Iterator<String> fields = groups.fieldNames();
                    while (fields.hasNext()) {
                        String f = fields.next();
                        if (!f.startsWith("_")) count++;
                    }
                    if (count > 1) {
                        return localizedName(groupNode, locale);
                    }
                }
            }
        }
        // Tires also have multiple groups
        if ("tires".equals(PRODUCT_STREAM_TO_FAMILY.get(productStream))) {
            JsonNode tiresGroups = configData.at("/kf_code_structure/groups/tires");
            if (tiresGroups != null && !tiresGroups.isMissingNode()) {
                JsonNode groupNode = tiresGroups.get(group);
                if (groupNode != null && !groupNode.isMissingNode()) {
                    return localizedName(groupNode, locale);
                }
            }
        }
        return null;
    }

    private String localizedName(JsonNode node, String locale) {
        String nameField = "hu".equals(locale) ? "name_hu" : "name_en";
        String fallbackField = "hu".equals(locale) ? "name_en" : "name_hu";
        if (node.has(nameField)) return node.get(nameField).asText();
        if (node.has(fallbackField)) return node.get(fallbackField).asText();
        return "";
    }

    private String localizedDefault(String en, String hu, String locale) {
        return "hu".equals(locale) ? hu : en;
    }

    private boolean isRefOnly(JsonNode node) {
        // A ref-only section has a _ref field but no numeric child keys
        if (!node.has("_ref")) return false;
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String name = fieldNames.next();
            if (!name.startsWith("_") && name.matches("\\d+")) {
                return false; // Has enumerated options
            }
        }
        return true;
    }

    private List<WizardOption> extractOptions(JsonNode node, String locale) {
        List<WizardOption> options = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String code = entry.getKey();
            if (code.startsWith("_")) continue; // skip metadata
            JsonNode optNode = entry.getValue();
            // Skip nested objects that aren't leaf options (e.g., EEE categories in subgroups)
            if (!optNode.has("name_hu") && !optNode.has("name_en")) continue;
            options.add(new WizardOption(code, localizedName(optNode, locale), null));
        }
        options.sort(Comparator.comparing(WizardOption::code));
        return options;
    }

    /**
     * Handles ref-only material stream sections by deriving options from fee_rates.
     */
    private WizardStepResult handleRefOnlyMaterialStreams(JsonNode configData,
                                                          String productStreamCode,
                                                          String family, String locale) {
        // EEE: each product stream IS its own category, auto-select "01"
        if (family.equals("eee")) {
            String label = localizedDefault("Default category", "Alapértelmezett kategória", locale);
            WizardOption autoOption = new WizardOption("01", label, null);
            return new WizardStepResult(List.of(autoOption), true, autoOption);
        }

        // Batteries: each product stream IS its own type, auto-select "01"
        if (family.equals("batteries")) {
            String label = localizedDefault("Default", "Alapértelmezett", locale);
            WizardOption autoOption = new WizardOption("01", label, null);
            return new WizardStepResult(List.of(autoOption), true, autoOption);
        }

        // Tires: auto-select "01"
        if (family.equals("tires")) {
            String label = localizedDefault("Default", "Alapértelmezett", locale);
            WizardOption autoOption = new WizardOption("01", label, null);
            return new WizardStepResult(List.of(autoOption), true, autoOption);
        }

        // Other plastic/chemical: derive from fee_rates_2026 keys
        if (family.equals("other_plastic_chemical")) {
            return deriveOptionsFromFeeRates(configData, productStreamCode, family, locale);
        }

        // Default: auto-select "01"
        String label = localizedDefault("Default", "Alapértelmezett", locale);
        WizardOption autoOption = new WizardOption("01", label, null);
        return new WizardStepResult(List.of(autoOption), true, autoOption);
    }

    /**
     * Derives material stream options from the fee_rates_2026 section.
     * Used for families where the material_streams section is ref-only.
     */
    private WizardStepResult deriveOptionsFromFeeRates(JsonNode configData, String productStreamCode,
                                                        String family, String locale) {
        String feeSection = FEE_SECTION_MAP.get(productStreamCode);
        JsonNode feeRates = configData.at("/fee_rates_2026/" + feeSection);
        if (feeRates == null || feeRates.isMissingNode()) {
            throw new IllegalArgumentException("Cannot derive options: fee rates section not found for " + family);
        }

        List<WizardOption> options = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = feeRates.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String feeCode = entry.getKey();
            if (feeCode.startsWith("_")) continue;
            // Extract material stream code (positions 3-4 of the fee code)
            if (feeCode.length() >= 4 && feeCode.startsWith(productStreamCode)) {
                String materialCode = feeCode.substring(2, 4);
                JsonNode feeNode = entry.getValue();
                options.add(new WizardOption(materialCode, localizedName(feeNode, locale), null));
            }
        }
        options.sort(Comparator.comparing(WizardOption::code));

        boolean autoSelect = options.size() == 1;
        return new WizardStepResult(options, autoSelect, autoSelect ? options.getFirst() : null);
    }

    /**
     * Expands packaging material options to align with fee code numbering.
     *
     * <p>The material_streams enumeration (01-07) does NOT map 1:1 to fee codes (01-11).
     * Specifically:
     * <ul>
     *   <li>Material stream "04" (Metal) splits into fee codes 04 (Iron/steel) and 05 (Aluminium)</li>
     *   <li>Material stream "05" (Glass) maps to fee code 06</li>
     *   <li>Material stream "06" (Textile) maps to fee code 07</li>
     *   <li>Material stream "07" (Composite) expands into fee codes 08-11 by dominant constituent</li>
     * </ul>
     *
     * <p>To ensure the wizard selection directly corresponds to the fee code suffix,
     * we replace the original 7 material streams with the 11 fee rate entries.
     * This gives the user a direct, unambiguous selection.
     */
    private List<WizardOption> expandPackagingMaterialOptions(JsonNode configData,
                                                               String productStreamCode, String locale) {
        String feeSection = FEE_SECTION_MAP.get(productStreamCode);
        JsonNode feeRates = configData.at("/fee_rates_2026/" + feeSection);
        if (feeRates == null || feeRates.isMissingNode()) {
            throw new IllegalArgumentException("Fee rates not found for packaging expansion: " + feeSection);
        }

        List<WizardOption> options = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = feeRates.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String feeCode = entry.getKey();
            if (feeCode.startsWith("_")) continue;
            if (feeCode.length() >= 4 && feeCode.startsWith(productStreamCode)) {
                String materialCode = feeCode.substring(2, 4);
                JsonNode feeNode = entry.getValue();
                options.add(new WizardOption(materialCode, localizedName(feeNode, locale), null));
            }
        }
        options.sort(Comparator.comparing(WizardOption::code));
        return options;
    }

    private String getAutoLabel(JsonNode configData, String family, String section, String locale) {
        JsonNode sectionNode = configData.at("/kf_code_structure/" + section + "/" + family);
        if (sectionNode != null && !sectionNode.isMissingNode()) {
            // Try to find "01" option
            JsonNode opt01 = sectionNode.get("01");
            if (opt01 != null && opt01.has("name_hu")) {
                return localizedName(opt01, locale);
            }
            if (sectionNode.has("_ref")) {
                return sectionNode.get("_ref").asText();
            }
        }
        return localizedDefault("Default", "Alapértelmezett", locale);
    }
}
