package hu.riskguard.epr.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.riskguard.core.util.HashUtil;
import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.datasource.domain.InvoiceDirection;
import hu.riskguard.datasource.domain.InvoiceLineItem;
import hu.riskguard.datasource.domain.InvoiceQueryResult;
import hu.riskguard.datasource.domain.InvoiceSummary;
import hu.riskguard.epr.api.dto.*;
import hu.riskguard.epr.internal.EprRepository;
import hu.riskguard.epr.internal.EprRepository.TemplateCopyData;
import hu.riskguard.jooq.tables.records.EprMaterialTemplatesRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Domain service facade for EPR (KGyfR) operations.
 * This is the ONLY public entry point into the epr module's business logic.
 *
 * <p>Follows the module facade pattern: Controller → EprService → EprRepository.
 * External modules must use this facade (or application events) — never the repository directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EprService {

    private final EprRepository eprRepository;
    private final DagEngine dagEngine;
    private final FeeCalculator feeCalculator;
    private final MohuExporter mohuExporter;
    private final EprConfigValidator eprConfigValidator;
    private final DataSourceService dataSourceService;

    /**
     * ObjectMapper for JSON serialization of traversal paths and config parsing.
     * Not injected from Spring context to avoid dependency on JacksonAutoConfiguration
     * (some integration test contexts may not load it).
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Config cache: version → parsed JsonNode. Config versions are immutable once activated.
     */
    private final ConcurrentHashMap<Integer, JsonNode> configCache = new ConcurrentHashMap<>();

    /**
     * KF-code list cache: "kfcodes-{version}-{locale}" → list. Config data is immutable per version.
     */
    private final ConcurrentHashMap<String, java.util.List<DagEngine.KfCodeEntry>> kfCodeCache = new ConcurrentHashMap<>();

    /**
     * Health-check method to verify the module is wired correctly.
     */
    public boolean isHealthy() {
        return true;
    }

    /**
     * Create a new material template.
     * Null recurring defaults to true (recurring by default).
     *
     * @return the newly generated template UUID
     */
    public UUID createTemplate(UUID tenantId, String name, BigDecimal baseWeightGrams, Boolean recurring) {
        boolean isRecurring = recurring == null || recurring;
        return eprRepository.insertTemplate(tenantId, name, baseWeightGrams, isRecurring);
    }

    /**
     * List all material templates for a tenant with override metadata from the latest linked calculation.
     */
    public List<MaterialTemplateResponse> listTemplatesWithOverride(UUID tenantId) {
        return eprRepository.findAllByTenantWithOverride(tenantId).stream()
                .map(two -> MaterialTemplateResponse.from(
                        two.template(), two.overrideKfCode(), two.overrideReason(), two.confidence(), two.feeRate()))
                .toList();
    }

    /**
     * Update a material template's name, base weight, and recurring flag.
     * Null recurring defaults to true (recurring by default).
     *
     * @return true if the template was found and updated
     */
    public boolean updateTemplate(UUID id, UUID tenantId, String name, BigDecimal baseWeightGrams, Boolean recurring) {
        boolean isRecurring = recurring == null || recurring;
        return eprRepository.updateTemplate(id, tenantId, name, baseWeightGrams, isRecurring);
    }

    /**
     * Delete a material template. ON DELETE SET NULL handles linked calculations.
     *
     * @return true if the template was found and deleted
     */
    public boolean deleteTemplate(UUID id, UUID tenantId) {
        return eprRepository.deleteTemplate(id, tenantId);
    }

    /**
     * Toggle the recurring flag on a material template.
     *
     * @return true if the template was found and updated
     */
    public boolean toggleRecurring(UUID id, UUID tenantId, boolean recurring) {
        return eprRepository.updateRecurring(id, tenantId, recurring);
    }

    /**
     * Copy templates from a previous quarter into the current quarter.
     * Copied templates get new UUIDs, verified=false, kf_code=null, created_at=now().
     *
     * @param tenantId            the tenant
     * @param sourceYear          the source year
     * @param sourceQuarter       the source quarter (1-4)
     * @param includeNonRecurring whether to include non-recurring (one-time) templates
     * @return list of newly created template UUIDs
     */
    @Transactional
    public List<UUID> copyFromQuarter(UUID tenantId, int sourceYear, int sourceQuarter, boolean includeNonRecurring) {
        List<EprMaterialTemplatesRecord> sourceTemplates =
                eprRepository.findByTenantAndQuarter(tenantId, sourceYear, sourceQuarter);

        Set<String> existingNames = eprRepository.findAllByTenant(tenantId).stream()
                .map(EprMaterialTemplatesRecord::getName)
                .collect(Collectors.toSet());

        List<TemplateCopyData> toCopy = sourceTemplates.stream()
                .filter(t -> includeNonRecurring || t.getRecurring())
                .filter(t -> !existingNames.contains(t.getName()))
                .map(t -> new TemplateCopyData(t.getName(), t.getBaseWeightGrams(), t.getRecurring()))
                .toList();

        return eprRepository.bulkInsertTemplates(tenantId, toCopy);
    }

    /**
     * Find a single template by ID and tenant — used for ownership verification.
     */
    public Optional<EprMaterialTemplatesRecord> findTemplate(UUID id, UUID tenantId) {
        return eprRepository.findByIdAndTenant(id, tenantId);
    }

    /**
     * Find multiple templates by their IDs, scoped to a tenant.
     * Used to efficiently fetch only the newly copied templates after bulk insert.
     */
    public List<EprMaterialTemplatesRecord> findTemplatesByIds(List<UUID> ids, UUID tenantId) {
        return eprRepository.findByIdsAndTenant(ids, tenantId);
    }

    // ─── Filing methods ──────────────────────────────────────────────────────

    /**
     * Compute EPR filing liability for a set of (templateId, quantityPcs) lines.
     * Validates: templates exist, belong to tenant, are verified, have a fee_rate.
     * Uses FeeCalculator for per-line and total computation.
     *
     * @throws ResponseStatusException 404 if any templateId not found or doesn't belong to tenant
     * @throws ResponseStatusException 422 if any template is not verified or has no fee_rate
     */
    @Transactional(readOnly = true)
    public FilingCalculationResponse calculateFiling(List<FilingLineRequest> lines, UUID tenantId) {
        // Reject duplicate templateIds before any DB work
        Set<UUID> seen = new HashSet<>();
        for (FilingLineRequest line : lines) {
            if (!seen.add(line.templateId())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Duplicate templateId in request: " + line.templateId());
            }
        }

        // Build a lookup: templateId → TemplateWithOverride (includes fee_rate)
        Map<UUID, EprRepository.TemplateWithOverride> templateMap = eprRepository.findAllByTenantWithOverride(tenantId)
                .stream()
                .collect(Collectors.toMap(t -> t.template().getId(), t -> t));

        List<FilingLineResultDto> resultLines = new ArrayList<>();
        List<FeeCalculator.FilingLineResult> calculatedLines = new ArrayList<>();
        for (FilingLineRequest line : lines) {
            EprRepository.TemplateWithOverride two = templateMap.get(line.templateId());
            if (two == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Template not found: " + line.templateId());
            }
            if (!Boolean.TRUE.equals(two.template().getVerified())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Template is not verified: " + line.templateId());
            }
            BigDecimal feeRate = two.feeRate();
            if (feeRate == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Template has no fee rate — run the wizard first: " + line.templateId());
            }
            BigDecimal baseWeightGrams = two.template().getBaseWeightGrams();
            if (baseWeightGrams == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Template has no base weight: " + line.templateId());
            }
            FeeCalculator.FilingLineResult result = feeCalculator.computeLine(
                    line.quantityPcs(), baseWeightGrams, feeRate);
            calculatedLines.add(result);
            resultLines.add(new FilingLineResultDto(
                    two.template().getId(),
                    two.template().getName(),
                    two.template().getKfCode(),
                    line.quantityPcs(),
                    baseWeightGrams,
                    result.totalWeightGrams(),
                    result.totalWeightKg(),
                    feeRate,
                    result.feeAmountHuf()
            ));
        }
        FeeCalculator.FilingTotals totals = feeCalculator.computeTotals(calculatedLines);
        return FilingCalculationResponse.from(resultLines, totals.totalWeightKg(), totals.totalFeeHuf(),
                getActiveConfigVersion());
    }

    /**
     * Generate a MOHU-compliant CSV export for the given filing lines, log the export in
     * {@code epr_exports}, and return the raw CSV bytes.
     *
     * <p>The config version in the request is validated against the currently active config —
     * if a newer config has been activated since the user last calculated, we reject to prevent
     * stale-data exports.
     *
     * @param request  the export request with lines and config version
     * @param tenantId the tenant from the JWT
     * @return raw UTF-8 BOM + CSV bytes ready to send as a file download
     * @throws ResponseStatusException 422 if configVersion does not match the active config
     */
    @Transactional
    public byte[] exportMohuCsv(MohuExportRequest request, UUID tenantId) {
        int activeVersion = getActiveConfigVersion();
        if (request.configVersion() != activeVersion) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Config version mismatch: request has version " + request.configVersion()
                    + " but active version is " + activeVersion);
        }
        byte[] csvBytes = mohuExporter.generate(request.lines());
        String fileHash = HashUtil.sha256Hex(csvBytes);
        eprRepository.saveExport(tenantId, request.configVersion(), fileHash);
        return csvBytes;
    }

    // ─── Wizard methods ──────────────────────────────────────────────────────

    /**
     * Start the wizard: returns root-level product stream options.
     */
    public WizardStartResponse startWizard(int configVersion, String locale) {
        JsonNode configData = loadConfig(configVersion);
        List<DagEngine.WizardOption> options = dagEngine.getProductStreams(configData, locale);
        return WizardStartResponse.from(configVersion, options);
    }

    /**
     * Process a wizard step: validates the selection and returns next-level options.
     */
    public WizardStepResponse processStep(WizardStepRequest request, String locale) {
        JsonNode configData = loadConfig(request.configVersion());
        WizardSelection selection = request.selection();
        List<WizardSelection> fullPath = new java.util.ArrayList<>(request.traversalPath());
        fullPath.add(selection);

        String nextLevel;
        DagEngine.WizardStepResult stepResult;

        switch (selection.level()) {
            case "product_stream" -> {
                nextLevel = "material_stream";
                stepResult = dagEngine.getMaterialStreams(configData, selection.code(), locale);
            }
            case "material_stream" -> {
                nextLevel = "group";
                String productStream = findSelectionCode(request.traversalPath(), "product_stream");
                stepResult = dagEngine.getGroups(configData, productStream, selection.code(), locale);
            }
            case "group" -> {
                nextLevel = "subgroup";
                String productStream = findSelectionCode(request.traversalPath(), "product_stream");
                String materialStream = findSelectionCode(fullPath, "material_stream");
                stepResult = dagEngine.getSubgroups(configData, productStream, materialStream,
                        selection.code(), locale);
            }
            default -> throw new IllegalArgumentException("Invalid wizard level: " + selection.level());
        }

        return WizardStepResponse.from(request.configVersion(), selection.level(), nextLevel, stepResult, fullPath);
    }

    /**
     * Resolve the final KF-code from a complete traversal.
     *
     * @param locale user locale for localized classification label (e.g., "hu", "en")
     */
    public WizardResolveResponse resolveKfCode(List<WizardSelection> traversalPath, int configVersion, String locale) {
        JsonNode configData = loadConfig(configVersion);
        String productStream = findSelectionCode(traversalPath, "product_stream");
        String materialStream = findSelectionCode(traversalPath, "material_stream");
        String group = findSelectionCode(traversalPath, "group");
        String subgroup = findSelectionCode(traversalPath, "subgroup");

        DagEngine.KfCodeResolution resolution = dagEngine.resolveKfCode(
                configData, productStream, materialStream, group, subgroup, locale);

        return WizardResolveResponse.from(resolution, traversalPath);
    }

    /**
     * Convenience overload that defaults locale to Hungarian.
     */
    public WizardResolveResponse resolveKfCode(List<WizardSelection> traversalPath, int configVersion) {
        return resolveKfCode(traversalPath, configVersion, "hu");
    }

    /**
     * Enumerate all valid KF-codes from the config hierarchy.
     * Results are cached per version+locale since config data is immutable.
     */
    public KfCodeListResponse getAllKfCodes(int configVersion, String locale) {
        String cacheKey = "kfcodes-" + configVersion + "-" + locale;
        java.util.List<DagEngine.KfCodeEntry> entries = kfCodeCache.computeIfAbsent(cacheKey, k -> {
            JsonNode configData = loadConfig(configVersion);
            return dagEngine.enumerateAllKfCodes(configData, locale);
        });
        return KfCodeListResponse.from(configVersion, entries);
    }

    /**
     * Confirm the wizard result: persist calculation and optionally link to template.
     * Handles manual override: if overrideKfCode is present, validates it and uses it for the template update.
     * Uses @Transactional for atomicity (insert calculation + update template).
     */
    @Transactional
    public WizardConfirmResponse confirmWizard(WizardConfirmRequest request, UUID tenantId) {
        // Validate confidence score
        try {
            DagEngine.Confidence.valueOf(request.confidenceScore());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid confidence score: " + request.confidenceScore()
                    + ". Must be HIGH, MEDIUM, or LOW.");
        }

        // Validate override KF-code exists in config if provided
        String effectiveKfCode = request.kfCode();
        if (request.overrideKfCode() != null && !request.overrideKfCode().isBlank()) {
            // Use cached KF-code list (via getAllKfCodes) instead of calling DagEngine directly
            java.util.List<hu.riskguard.epr.api.dto.KfCodeEntry> allCodes =
                    getAllKfCodes(request.configVersion(), "hu").entries();
            boolean valid = allCodes.stream().anyMatch(e -> e.kfCode().equals(request.overrideKfCode()));
            if (!valid) {
                throw new IllegalArgumentException("Override KF-code not found in active config: " + request.overrideKfCode());
            }
            effectiveKfCode = request.overrideKfCode();
        }

        // Serialize traversal path to JSONB
        JSONB traversalPathJsonb;
        try {
            String json = OBJECT_MAPPER.writeValueAsString(request.traversalPath());
            traversalPathJsonb = JSONB.jsonb(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize traversal path", e);
        }

        // Insert calculation record with confidence and override fields
        UUID calculationId = eprRepository.insertCalculation(
                tenantId,
                request.configVersion(),
                traversalPathJsonb,
                request.materialClassification(),
                request.kfCode(),
                request.feeRate(),
                request.templateId(),
                request.confidenceScore(),
                request.overrideKfCode(),
                request.overrideReason()
        );

        // Optionally update the linked template with the effective KF-code
        boolean templateUpdated = false;
        if (request.templateId() != null) {
            try {
                templateUpdated = eprRepository.updateTemplateKfCode(
                        request.templateId(), tenantId, effectiveKfCode);
                if (!templateUpdated) {
                    log.warn("Template {} not found for tenant {} — calculation {} saved without link",
                            request.templateId(), tenantId, calculationId);
                }
            } catch (Exception e) {
                log.warn("Failed to update template {} — calculation {} saved without link",
                        request.templateId(), calculationId, e);
                templateUpdated = false;
            }
        }

        return WizardConfirmResponse.from(calculationId, effectiveKfCode, templateUpdated);
    }

    /**
     * Retry linking a saved calculation's KF-code to a material template.
     * Loads the calculation (tenant-scoped), extracts the effective KF-code
     * (override takes precedence), and retries the template update.
     *
     * @param calculationId the UUID of an existing epr_calculations record
     * @param templateId    the UUID of the template to link
     * @param tenantId      the tenant from the JWT (security: must own the calculation)
     * @return result indicating whether the template was updated
     */
    @Transactional
    public RetryLinkResponse retryLink(UUID calculationId, UUID templateId, UUID tenantId) {
        var calc = eprRepository.findCalculationById(calculationId, tenantId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Calculation not found"));

        // Validate that the provided templateId matches the calculation's linked template
        UUID calcTemplateId = calc.get("template_id", UUID.class);
        if (calcTemplateId != null && !calcTemplateId.equals(templateId)) {
            log.warn("retryLink templateId mismatch: calculation {} linked to template {}, but request specifies {}",
                    calculationId, calcTemplateId, templateId);
        }

        String overrideKfCode = calc.get("override_kf_code", String.class);
        String originalKfCode = calc.get("kf_code", String.class);
        String effectiveKfCode = (overrideKfCode != null && !overrideKfCode.isBlank())
                ? overrideKfCode : originalKfCode;

        if (effectiveKfCode == null || effectiveKfCode.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Calculation has no KF-code to link");
        }

        boolean updated = eprRepository.updateTemplateKfCode(templateId, tenantId, effectiveKfCode);

        return RetryLinkResponse.from(updated, effectiveKfCode);
    }

    // ─── Invoice auto-fill ──────────────────────────────────────────────────

    /**
     * Returns the tax number stored in NAV credentials for the given tenant, if any.
     * Used by the frontend to pre-populate the auto-fill tax number field.
     *
     * @param tenantId the tenant UUID from JWT
     * @return the registered 8-digit tax number, or empty if no credentials configured
     */
    public Optional<String> getRegisteredTaxNumber(UUID tenantId) {
        return dataSourceService.getTenantTaxNumber(tenantId);
    }

    /**
     * Builds a pre-filled EPR filing suggestion from outbound invoices fetched from NAV Online Számla.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Fetch invoice summaries for the given tax number, date range, and OUTBOUND direction.</li>
     *   <li>For each summary, fetch full invoice details with line items.</li>
     *   <li>Collect all line items where {@code vtszCode != null}.</li>
     *   <li>Group by VTSZ code and sum quantities.</li>
     *   <li>Load active EPR config, extract {@code vtszMappings}, match by longest prefix.</li>
     *   <li>Load tenant templates, match by {@code materialName_hu} (case-insensitive).</li>
     *   <li>Return result with {@code navAvailable=false} if query returned empty due to error.</li>
     * </ol>
     *
     * @param taxNumber company's 8-digit tax number
     * @param from      start of date range (inclusive)
     * @param to        end of date range (inclusive)
     * @param tenantId  tenant UUID from JWT
     * @return auto-fill response with suggested lines, NAV availability flag, and mode
     */
    public InvoiceAutoFillResponse autoFillFromInvoices(String taxNumber, LocalDate from, LocalDate to, UUID tenantId) {
        // D2: Validate tenant owns the requested tax number (skip in demo mode / no credentials)
        dataSourceService.getTenantTaxNumber(tenantId).ifPresent(registeredTaxNumber -> {
            if (!taxNumber.startsWith(registeredTaxNumber) && !registeredTaxNumber.startsWith(taxNumber)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Tax number does not match tenant's registered tax number");
            }
        });

        // Step 1: fetch invoice summaries with availability flag
        InvoiceQueryResult queryResult = dataSourceService.queryInvoices(taxNumber, from, to, InvoiceDirection.OUTBOUND);
        List<InvoiceSummary> summaries = queryResult.summaries();
        boolean navAvailable = queryResult.serviceAvailable();

        // Step 2+3: fetch details and collect VTSZ line items, grouped by vtszCode+unit (D4)
        record VtszUnitKey(String vtszCode, String unit) {}
        Map<VtszUnitKey, BigDecimal> vtszQuantities = new LinkedHashMap<>();

        for (InvoiceSummary summary : summaries) {
            var detail = dataSourceService.queryInvoiceDetails(summary.invoiceNumber());
            for (InvoiceLineItem item : detail.lineItems()) {
                if (item.vtszCode() != null && !item.vtszCode().isBlank()) {
                    // P4: skip lines where quantity is null or ≤ 0
                    if (item.quantity() == null || item.quantity().compareTo(BigDecimal.ZERO) <= 0) continue;
                    String unit = item.unitOfMeasure() != null ? item.unitOfMeasure() : "DARAB";
                    vtszQuantities.merge(new VtszUnitKey(item.vtszCode(), unit), item.quantity(), BigDecimal::add);
                }
            }
        }

        // Step 5: load active config, extract vtszMappings
        List<VtszMapping> vtszMappings = loadVtszMappings();

        // Step 6: load tenant templates for matching
        List<EprMaterialTemplatesRecord> templates = eprRepository.findAllByTenant(tenantId);

        // Step 4+5+6: build result lines
        List<InvoiceAutoFillLineDto> lines = vtszQuantities.entrySet().stream()
                .map(entry -> {
                    String vtszCode = entry.getKey().vtszCode();
                    String unit = entry.getKey().unit();
                    BigDecimal quantity = entry.getValue();

                    // Find best matching VTSZ mapping (longest prefix wins)
                    VtszMapping bestMapping = vtszMappings.stream()
                            .filter(m -> vtszCode.startsWith(m.vtszPrefix()))
                            .max(Comparator.comparingInt(m -> m.vtszPrefix().length()))
                            .orElse(null);

                    String suggestedKfCode = bestMapping != null ? bestMapping.kfCode() : null;
                    String description = bestMapping != null ? bestMapping.materialName_hu() : vtszCode;

                    // Find matching template by materialName_hu (case-insensitive)
                    EprMaterialTemplatesRecord matchedTemplate = null;
                    if (bestMapping != null) {
                        String targetName = bestMapping.materialName_hu();
                        matchedTemplate = templates.stream()
                                .filter(t -> targetName.equalsIgnoreCase(t.getName()))
                                .findFirst()
                                .orElse(null);
                    }

                    return new InvoiceAutoFillLineDto(
                            vtszCode,
                            description,
                            suggestedKfCode,
                            quantity,
                            unit,
                            matchedTemplate != null,
                            matchedTemplate != null ? matchedTemplate.getId() : null
                    );
                })
                .toList();

        return InvoiceAutoFillResponse.from(lines, navAvailable, dataSourceService.getMode());
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
                log.warn("loadVtszMappings: config_data is null in active config record");
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
            log.warn("Failed to load vtszMappings from active config: {}", e.getMessage());
            return List.of();
        }
    }

    private record VtszMapping(String vtszPrefix, String kfCode, String materialName_hu) {}

    // ─── Admin: EPR config management ───────────────────────────────────────

    /**
     * Returns the full active config including version, raw JSON, and activation timestamp.
     */
    public EprConfigResponse getActiveConfigFull() {
        return eprRepository.findActiveConfig()
                .map(EprConfigResponse::from)
                .orElseThrow(() -> new IllegalStateException("No active EPR config found"));
    }

    /**
     * Validates a candidate EPR config JSON using the 5 validation cases.
     */
    public EprConfigValidateResponse validateNewConfig(String configData) {
        return eprConfigValidator.validate(configData);
    }

    /**
     * Atomically publishes a new EPR config version and logs the admin action.
     * Re-validates on the server before inserting to prevent client-side bypass.
     * Does NOT evict or pre-populate configCache — the new version is loaded on-demand.
     */
    @Transactional
    public EprConfigPublishResponse publishNewConfig(String configData, UUID actorUserId) {
        EprConfigValidateResponse validation = eprConfigValidator.validate(configData);
        if (!validation.valid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Config validation failed: " + String.join("; ", validation.errors()));
        }
        int previousVersion = getActiveConfigVersion();
        int newVersion = eprRepository.getMaxConfigVersion() + 1;
        OffsetDateTime activatedAt = OffsetDateTime.now();
        eprRepository.insertConfig(newVersion, configData, activatedAt);
        String details = "{\"version\":" + newVersion + ",\"previous_version\":" + previousVersion + "}";
        eprRepository.insertAdminActionLog(actorUserId, "PUBLISH_EPR_CONFIG",
                "epr_config:v" + newVersion, details);
        return new EprConfigPublishResponse(newVersion, activatedAt.toInstant());
    }

    /**
     * Get the latest activated config version.
     */
    public int getActiveConfigVersion() {
        return eprRepository.findActiveConfig()
                .map(r -> r.get("version", Integer.class))
                .orElseThrow(() -> new IllegalStateException("No active EPR config found"));
    }

    // ─── Config loading ──────────────────────────────────────────────────────

    private JsonNode loadConfig(int version) {
        return configCache.computeIfAbsent(version, v -> {
            Record record = eprRepository.findConfigByVersion(v)
                    .orElseThrow(() -> new IllegalArgumentException("Config version not found: " + v));
            Object configDataObj = record.get("config_data");
            try {
                if (configDataObj instanceof JSONB jsonb) {
                    return OBJECT_MAPPER.readTree(jsonb.data());
                }
                return OBJECT_MAPPER.readTree(configDataObj.toString());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse config_data for version " + v, e);
            }
        });
    }

    private String findSelectionCode(List<WizardSelection> path, String level) {
        return path.stream()
                .filter(s -> level.equals(s.level()))
                .findFirst()
                .map(WizardSelection::code)
                .orElseThrow(() -> new IllegalArgumentException("Missing " + level + " in traversal path"));
    }
}
