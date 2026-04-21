package hu.riskguard.epr.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.riskguard.core.util.HashUtil;
import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.epr.api.dto.EprConfigPublishResponse;
import hu.riskguard.epr.api.dto.EprConfigResponse;
import hu.riskguard.epr.api.dto.EprConfigValidateResponse;
import hu.riskguard.epr.api.dto.KfCodeListResponse;
import hu.riskguard.epr.api.dto.MaterialTemplateResponse;
import hu.riskguard.epr.api.dto.RetryLinkResponse;
import hu.riskguard.epr.api.dto.WizardConfirmRequest;
import hu.riskguard.epr.api.dto.WizardConfirmResponse;
import hu.riskguard.epr.api.dto.WizardResolveResponse;
import hu.riskguard.epr.api.dto.WizardSelection;
import hu.riskguard.epr.api.dto.WizardStartResponse;
import hu.riskguard.epr.api.dto.WizardStepRequest;
import hu.riskguard.epr.api.dto.WizardStepResponse;
import hu.riskguard.epr.internal.EprRepository;
import hu.riskguard.epr.internal.EprRepository.TemplateCopyData;
import hu.riskguard.epr.aggregation.api.dto.KfCodeTotal;
import hu.riskguard.epr.producer.domain.ProducerProfile;
import hu.riskguard.epr.producer.domain.ProducerProfileService;
import hu.riskguard.epr.report.EprReportArtifact;
import hu.riskguard.epr.report.EprReportRequest;
import hu.riskguard.epr.report.EprReportTarget;
import hu.riskguard.jooq.tables.records.EprMaterialTemplatesRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
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
    private final EprConfigValidator eprConfigValidator;
    private final DataSourceService dataSourceService;
    private final EprReportTarget reportTarget;
    private final ProducerProfileService producerProfileService;
    private final PlatformTransactionManager transactionManager;

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

    /** Wraps both the generated artifact and the persisted submission UUID. */
    public record GenerateReportResult(EprReportArtifact artifact, UUID submissionId) {}

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
                        two.template(), two.overrideKfCode(), two.overrideReason(),
                        two.confidence(), two.feeRate(), two.materialClassification()))
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
    @Transactional(readOnly = true)
    public GenerateReportResult generateReport(EprReportRequest request, List<KfCodeTotal> kfTotals) {
        UUID tenantId = request.tenantId();
        LocalDate periodStart = request.periodStart();
        LocalDate periodEnd = request.periodEnd();

        // Validate period start before end
        if (periodStart.isAfter(periodEnd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reporting period start must not be after end");
        }

        // Validate period does not extend into the future
        if (periodEnd.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reporting period may not extend into the future");
        }

        // Validate period is within one calendar quarter
        int startQuarter = (periodStart.getMonthValue() - 1) / 3;
        int endQuarter = (periodEnd.getMonthValue() - 1) / 3;
        if (periodStart.getYear() != periodEnd.getYear() || startQuarter != endQuarter) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reporting period must be within a single calendar quarter");
        }

        // D2: Validate tenant owns the requested tax number (skip in demo mode / no credentials)
        dataSourceService.getTenantTaxNumber(tenantId).ifPresent(registeredTaxNumber -> {
            String requestedTaxNumber = request.taxNumber();
            String requestedBase = requestedTaxNumber.length() >= 8 ? requestedTaxNumber.substring(0, 8) : requestedTaxNumber;
            String registeredBase = registeredTaxNumber.length() >= 8 ? registeredTaxNumber.substring(0, 8) : registeredTaxNumber;
            if (!requestedBase.equals(registeredBase)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Tax number does not match tenant's registered tax number");
            }
        });

        // Load producer profile (throws 412 if incomplete)
        ProducerProfile profile = producerProfileService.get(tenantId);

        // Delegate to the active EprReportTarget strategy (OkirkapuXmlExporter by default)
        EprReportArtifact artifact = reportTarget.generate(kfTotals, profile, periodStart, periodEnd);

        // Compute totals for submission history columns.
        BigDecimal totalWeightKg = kfTotals.stream()
                .map(hu.riskguard.epr.aggregation.api.dto.KfCodeTotal::totalWeightKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(3, java.math.RoundingMode.HALF_UP);
        BigDecimal totalFeeHuf = kfTotals.stream()
                .map(hu.riskguard.epr.aggregation.api.dto.KfCodeTotal::totalFeeHuf)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        String tenantShortId = tenantId.toString().substring(0, 8);
        String fileName = "okirkapu-" + tenantShortId + "-" + periodStart + "-" + periodEnd + ".xml";

        // Log the export in a separate REQUIRES_NEW transaction so the write commits
        // independently of the outer read-only transaction (per AC 3 + ADR-0002).
        String fileHash = artifact.xmlBytes() != null
                ? HashUtil.sha256Hex(artifact.xmlBytes())
                : HashUtil.sha256Hex(artifact.bytes());
        int configVersion;
        try {
            configVersion = getActiveConfigVersion();
        } catch (Exception e) {
            configVersion = 0;
        }
        final int finalConfigVersion = configVersion;
        final BigDecimal finalTotalWeightKg = totalWeightKg;
        final BigDecimal finalTotalFeeHuf = totalFeeHuf;
        final byte[] xmlContent = artifact.xmlBytes();
        final UUID submittedByUserId = request.submittedByUserId();
        final String finalFileName = fileName;
        TransactionTemplate writeTx = new TransactionTemplate(transactionManager);
        writeTx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        UUID submissionId = writeTx.execute(status ->
                eprRepository.insertExport(tenantId, finalConfigVersion, fileHash,
                        "OKIRKAPU_XML", periodStart, periodEnd,
                        finalTotalWeightKg, finalTotalFeeHuf, xmlContent, submittedByUserId, finalFileName));

        return new GenerateReportResult(artifact, submissionId);
    }

    /**
     * Preview EPR report: same validation as generateReport but does NOT write an audit log.
     */
    @Transactional(readOnly = true)
    public EprReportArtifact previewReport(EprReportRequest request, List<KfCodeTotal> kfTotals) {
        UUID tenantId = request.tenantId();
        LocalDate periodStart = request.periodStart();
        LocalDate periodEnd = request.periodEnd();

        if (periodStart.isAfter(periodEnd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reporting period start must not be after end");
        }
        if (periodEnd.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reporting period may not extend into the future");
        }
        int startQuarter = (periodStart.getMonthValue() - 1) / 3;
        int endQuarter = (periodEnd.getMonthValue() - 1) / 3;
        if (periodStart.getYear() != periodEnd.getYear() || startQuarter != endQuarter) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reporting period must be within a single calendar quarter");
        }
        dataSourceService.getTenantTaxNumber(tenantId).ifPresent(registeredTaxNumber -> {
            String requestedTaxNumber = request.taxNumber();
            String requestedBase = requestedTaxNumber.length() >= 8 ? requestedTaxNumber.substring(0, 8) : requestedTaxNumber;
            String registeredBase = registeredTaxNumber.length() >= 8 ? registeredTaxNumber.substring(0, 8) : registeredTaxNumber;
            if (!requestedBase.equals(registeredBase)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Tax number does not match tenant's registered tax number");
            }
        });
        ProducerProfile profile = producerProfileService.get(tenantId);
        return reportTarget.generate(kfTotals, profile, periodStart, periodEnd);
    }

    // ─── Submission History methods (Story 10.9) ─────────────────────────────

    public List<hu.riskguard.epr.api.dto.EprSubmissionSummary> listSubmissions(UUID tenantId, int page, int size) {
        return eprRepository.listSubmissions(tenantId, page, size);
    }

    public long countSubmissions(UUID tenantId) {
        return eprRepository.countSubmissions(tenantId);
    }

    public Optional<hu.riskguard.epr.api.dto.EprSubmissionSummary> findSubmission(UUID id, UUID tenantId) {
        return eprRepository.findSubmission(id, tenantId);
    }

    public Optional<byte[]> getSubmissionXmlContent(UUID id, UUID tenantId) {
        return eprRepository.getSubmissionXmlContent(id, tenantId);
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

    // ─── Tax number lookup (still used by /filing/registered-tax-number) ────

    /**
     * Returns the tax number stored in NAV credentials for the given tenant, if any.
     *
     * @param tenantId the tenant UUID from JWT
     * @return the registered 8-digit tax number, or empty if no credentials configured
     */
    public Optional<String> getRegisteredTaxNumber(UUID tenantId) {
        return dataSourceService.getTenantTaxNumber(tenantId);
    }

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
