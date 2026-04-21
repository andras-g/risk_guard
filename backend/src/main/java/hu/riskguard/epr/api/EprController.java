package hu.riskguard.epr.api;

import hu.riskguard.core.security.Tier;
import hu.riskguard.core.security.TierRequired;
import hu.riskguard.core.util.JwtUtil;
import hu.riskguard.epr.api.dto.CopyQuarterRequest;
import hu.riskguard.epr.api.dto.KfCodeListResponse;
import hu.riskguard.epr.api.dto.MaterialTemplateRequest;
import hu.riskguard.epr.api.dto.MaterialTemplateResponse;
import hu.riskguard.epr.aggregation.api.dto.ProvenanceLine;
import hu.riskguard.epr.aggregation.api.dto.ProvenancePage;
import hu.riskguard.epr.aggregation.domain.AggregationProvenanceLine;
import hu.riskguard.epr.api.dto.OkirkapuExportRequest;
import hu.riskguard.epr.api.dto.RecurringToggleRequest;
import hu.riskguard.epr.audit.AuditService;
import hu.riskguard.epr.api.dto.RetryLinkRequest;
import hu.riskguard.epr.api.dto.RetryLinkResponse;
import hu.riskguard.epr.api.dto.WizardConfirmRequest;
import hu.riskguard.epr.api.dto.WizardConfirmResponse;
import hu.riskguard.epr.api.dto.WizardResolveRequest;
import hu.riskguard.epr.api.dto.WizardResolveResponse;
import hu.riskguard.epr.api.dto.WizardStartResponse;
import hu.riskguard.epr.api.dto.WizardStepRequest;
import hu.riskguard.epr.api.dto.WizardStepResponse;
import hu.riskguard.epr.aggregation.domain.InvoiceDrivenFilingAggregator;
import hu.riskguard.epr.domain.EprService;
import hu.riskguard.epr.producer.api.dto.ProducerProfileResponse;
import hu.riskguard.epr.producer.api.dto.ProducerProfileUpsertRequest;
import hu.riskguard.epr.producer.domain.ProducerProfileService;
import hu.riskguard.epr.report.EprReportArtifact;
import hu.riskguard.epr.report.EprReportRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for EPR (KGyfR) operations.
 * Delegates all business logic to {@link EprService} facade.
 *
 * <p>All endpoints require PRO_EPR tier. Tenant identity extracted from JWT claims.
 */
@RestController
@RequestMapping("/api/v1/epr")
@RequiredArgsConstructor
@TierRequired(Tier.PRO_EPR)
public class EprController {

    private static final int MAX_PROVENANCE_PAGE_SIZE = 500;

    private final EprService eprService;
    private final ProducerProfileService producerProfileService;
    private final InvoiceDrivenFilingAggregator aggregator;
    private final AuditService auditService;

    /**
     * Create a new material template.
     */
    @PostMapping("/materials")
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialTemplateResponse createTemplate(
            @Valid @RequestBody MaterialTemplateRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        UUID id = eprService.createTemplate(
                tenantId, request.name(), request.baseWeightGrams(), request.recurring());
        return eprService.findTemplate(id, tenantId)
                .map(MaterialTemplateResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to read back created template"));
    }

    /**
     * List all material templates for the current tenant.
     * Includes override metadata from the latest linked calculation.
     */
    @GetMapping("/materials")
    public List<MaterialTemplateResponse> listTemplates(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        return eprService.listTemplatesWithOverride(tenantId);
    }

    /**
     * Update a material template's name and base weight.
     */
    @PutMapping("/materials/{id}")
    public MaterialTemplateResponse updateTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody MaterialTemplateRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        boolean updated = eprService.updateTemplate(id, tenantId, request.name(), request.baseWeightGrams(), request.recurring());
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Material template not found");
        }
        return eprService.findTemplate(id, tenantId)
                .map(MaterialTemplateResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Material template not found"));
    }

    /**
     * Delete a material template. ON DELETE SET NULL handles linked calculations.
     */
    @DeleteMapping("/materials/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        boolean deleted = eprService.deleteTemplate(id, tenantId);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Material template not found");
        }
    }

    /**
     * Toggle the recurring flag on a material template.
     */
    @PatchMapping("/materials/{id}/recurring")
    public MaterialTemplateResponse toggleRecurring(
            @PathVariable UUID id,
            @Valid @RequestBody RecurringToggleRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        boolean updated = eprService.toggleRecurring(id, tenantId, request.recurring());
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Material template not found");
        }
        return eprService.findTemplate(id, tenantId)
                .map(MaterialTemplateResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Material template not found"));
    }

    /**
     * Copy templates from a previous quarter into new records for the current quarter.
     */
    @PostMapping("/materials/copy-from-quarter")
    public List<MaterialTemplateResponse> copyFromQuarter(
            @Valid @RequestBody CopyQuarterRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        List<UUID> newIds = eprService.copyFromQuarter(
                tenantId, request.sourceYear(), request.sourceQuarter(), request.includeNonRecurring());

        return eprService.findTemplatesByIds(newIds, tenantId).stream()
                .map(MaterialTemplateResponse::from)
                .toList();
    }

    // ─── Wizard endpoints ───────────────────────────────────────────────────

    /**
     * Enumerate all valid KF-codes from the active config.
     * Used by the manual override autocomplete to display all valid options.
     * Config version is optional — defaults to the latest activated version.
     */
    @GetMapping("/wizard/kf-codes")
    public KfCodeListResponse wizardKfCodes(
            @RequestParam(required = false) Integer configVersion) {
        String locale = LocaleContextHolder.getLocale().getLanguage();
        int version = configVersion != null ? configVersion : eprService.getActiveConfigVersion();
        return eprService.getAllKfCodes(version, locale);
    }

    /**
     * Start the wizard: returns root-level product stream options.
     * Config version is optional — defaults to the latest activated version.
     */
    @GetMapping("/wizard/start")
    public WizardStartResponse wizardStart(
            @RequestParam(required = false) Integer configVersion) {
        String locale = LocaleContextHolder.getLocale().getLanguage();
        int version = configVersion != null ? configVersion : eprService.getActiveConfigVersion();
        return eprService.startWizard(version, locale);
    }

    /**
     * Advance the wizard by one step: validate selection and return next options.
     */
    @PostMapping("/wizard/step")
    public WizardStepResponse wizardStep(
            @Valid @RequestBody WizardStepRequest request) {
        String locale = LocaleContextHolder.getLocale().getLanguage();
        return eprService.processStep(request, locale);
    }

    /**
     * Resolve the final KF-code from a complete 4-level traversal.
     */
    @PostMapping("/wizard/resolve")
    public WizardResolveResponse wizardResolve(
            @Valid @RequestBody WizardResolveRequest request) {
        String locale = LocaleContextHolder.getLocale().getLanguage();
        return eprService.resolveKfCode(request.traversalPath(), request.configVersion(), locale);
    }

    /**
     * Confirm the wizard result: persist calculation and link to template.
     * Tenant identity extracted from JWT — never from request body.
     */
    @PostMapping("/wizard/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public WizardConfirmResponse wizardConfirm(
            @Valid @RequestBody WizardConfirmRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        return eprService.confirmWizard(request, tenantId);
    }

    /**
     * Retry linking a saved calculation's KF-code to a template.
     * Used when the initial confirm saved the calculation but failed to update the template.
     * Tenant identity extracted from JWT — calculation must belong to the requesting tenant.
     */
    @PostMapping("/wizard/retry-link")
    public RetryLinkResponse wizardRetryLink(
            @Valid @RequestBody RetryLinkRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        return eprService.retryLink(request.calculationId(), request.templateId(), tenantId);
    }

    /**
     * Returns the tenant's registered tax number from NAV credentials, if any.
     * Used by the frontend to pre-populate the auto-fill tax number input.
     */
    @GetMapping("/filing/registered-tax-number")
    public ResponseEntity<Map<String, String>> getRegisteredTaxNumber(
            @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        String taxNumber = eprService.getRegisteredTaxNumber(tenantId).orElse("");
        return ResponseEntity.ok(Map.of("taxNumber", taxNumber));
    }

    /**
     * Tombstone endpoint — MOHU CSV export has been replaced by OKIRkapu XML export.
     * Returns HTTP 410 Gone with a redirect hint.
     */
    @PostMapping("/filing/export")
    public ResponseEntity<Map<String, String>> exportMohuGone() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(Map.of(
                        "code", "epr.export.csv.removed",
                        "message", "MOHU CSV export has been replaced by OKIRkapu XML export",
                        "replacement", "/api/v1/epr/filing/okirkapu-export"
                ));
    }

    /**
     * Generate OKIRkapu XML export for a quarter period.
     * Validates producer profile completeness, tax number ownership, and period boundaries.
     * Returns a ZIP file containing the XML and a summary text.
     */
    @PostMapping("/filing/okirkapu-export")
    public ResponseEntity<byte[]> exportOkirkapu(
            @Valid @RequestBody OkirkapuExportRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        var aggregationResult = aggregator.aggregateForPeriod(tenantId, request.from(), request.to());
        EprReportArtifact artifact = eprService.generateReport(
                new EprReportRequest(tenantId, request.from(), request.to(), request.taxNumber()),
                aggregationResult.kfTotals());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + artifact.filename() + "\"")
                .body(artifact.bytes());
    }

    // ─── Provenance endpoints ─────────────────────────────────────────────────

    /**
     * Paginated provenance: per-component lines showing how invoice lines drove KF-code totals.
     * AC #1-#8: page/size defaults, size clamped to 500, role + tier + profile guards.
     */
    @GetMapping("/filing/aggregation/provenance")
    public ResponseEntity<ProvenancePage> getProvenance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {

        JwtUtil.requireRole(jwt, "Provenance requires SME_ADMIN, ACCOUNTANT, or PLATFORM_ADMIN role",
                "SME_ADMIN", "ACCOUNTANT", "PLATFORM_ADMIN");
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        UUID userId = JwtUtil.requireUuidClaim(jwt, "user_id");

        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reporting period start must not be after end");
        }

        int safeSize = Math.max(1, Math.min(size, MAX_PROVENANCE_PAGE_SIZE));
        int safePage = Math.max(0, page);

        // 412 if producer profile is incomplete (throws ResponseStatusException)
        producerProfileService.get(tenantId);

        var result = aggregator.aggregateForPeriod(tenantId, from, to);
        List<AggregationProvenanceLine> all = result.provenanceLines();
        // long-cast guard against page * size overflow on pathological inputs
        long startIdx = (long) safePage * (long) safeSize;
        int fromIdx = (int) Math.min(startIdx, all.size());
        int toIdx = (int) Math.min(startIdx + safeSize, all.size());
        List<ProvenanceLine> pageContent = all.subList(fromIdx, toIdx).stream()
                .map(EprController::toDto)
                .toList();

        auditService.recordProvenanceFetch(tenantId, userId, from, to, safePage, safeSize);
        return ResponseEntity.ok(new ProvenancePage(pageContent, all.size(), safePage, safeSize));
    }

    /**
     * CSV export of full provenance dataset (no pagination). Streams to avoid OOM (AC #9-#11).
     * Flush every 500 rows; UTF-8 BOM; semicolon delimiter; Content-Disposition attachment.
     */
    @GetMapping(value = "/filing/aggregation/provenance.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<StreamingResponseBody> exportProvenanceCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt) {

        JwtUtil.requireRole(jwt, "Provenance CSV requires SME_ADMIN, ACCOUNTANT, or PLATFORM_ADMIN role",
                "SME_ADMIN", "ACCOUNTANT", "PLATFORM_ADMIN");
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        UUID userId = JwtUtil.requireUuidClaim(jwt, "user_id");

        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reporting period start must not be after end");
        }

        // 412 if producer profile is incomplete (throws ResponseStatusException)
        producerProfileService.get(tenantId);

        var result = aggregator.aggregateForPeriod(tenantId, from, to);
        List<AggregationProvenanceLine> lines = result.provenanceLines();

        String tenantShortId = tenantId.toString().substring(0, 8);
        String filename = "provenance-" + tenantShortId + "-" + from + "-" + to + ".csv";

        StreamingResponseBody body = outputStream -> {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            writer.print('\uFEFF'); // UTF-8 BOM
            writer.println("Számlaszám;Sorszám;VTSZ;Megnevezés;Mennyiség;ME;Termékazonosító;Terméknév;KF-kód;Csomagolási szint;Súly hozzájárulás (kg);Provenance tag");
            int rowCount = 0;
            for (AggregationProvenanceLine line : lines) {
                writer.println(formatCsvRow(line));
                if (++rowCount % 500 == 0) writer.flush();
            }
            writer.flush();
        };

        auditService.recordCsvExport(tenantId, userId, from, to);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    private static ProvenanceLine toDto(AggregationProvenanceLine p) {
        return new ProvenanceLine(
                p.invoiceNumber(), p.lineNumber(), p.vtsz(), p.description(),
                p.quantity(), p.unitOfMeasure(), p.resolvedProductId(), p.productName(),
                p.componentId(), p.wrappingLevel(), p.componentKfCode(),
                p.weightContributionKg(), p.provenanceTag());
    }

    private static String formatCsvRow(AggregationProvenanceLine p) {
        return String.join(";",
                csvEscape(p.invoiceNumber()),
                String.valueOf(p.lineNumber()),
                csvEscape(p.vtsz()),
                csvEscape(p.description()),
                p.quantity() != null ? p.quantity().toPlainString() : "",
                csvEscape(p.unitOfMeasure()),
                p.resolvedProductId() != null ? p.resolvedProductId().toString() : "",
                csvEscape(p.productName()),
                csvEscape(p.componentKfCode()),
                p.wrappingLevel() != null ? String.valueOf(p.wrappingLevel()) : "",
                p.weightContributionKg().setScale(4, RoundingMode.HALF_UP).toPlainString(),
                p.provenanceTag().name()
        );
    }

    private static String csvEscape(String value) {
        if (value == null) return "";
        // Neutralise Excel/LibreOffice formula injection (CWE-1236) by prefixing a
        // single-quote when the cell starts with =, +, -, @, \t, or \r. Invoice descriptions
        // can contain attacker-influenced text, so we apply this BEFORE delimiter handling.
        String safe = value;
        if (!safe.isEmpty()) {
            char first = safe.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
                safe = "'" + safe;
            }
        }
        if (safe.contains(";") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    // ─── Producer profile endpoints ──────────────────────────────────────────

    /**
     * Get the producer profile for the current tenant.
     * Returns null fields if the profile is incomplete (so the frontend form can load empty).
     * Requires SME_ADMIN role.
     */
    @GetMapping("/producer-profile")
    public ResponseEntity<ProducerProfileResponse> getProducerProfile(
            @AuthenticationPrincipal Jwt jwt) {
        requireSmeAdminRole(jwt);
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        return producerProfileService.getForDisplay(tenantId)
                .map(p -> ResponseEntity.ok(ProducerProfileResponse.from(p)))
                .orElse(ResponseEntity.ok().build());
    }

    /**
     * Create or update the producer profile for the current tenant.
     * Requires SME_ADMIN role.
     */
    @PutMapping("/producer-profile")
    public ResponseEntity<ProducerProfileResponse> upsertProducerProfile(
            @Valid @RequestBody ProducerProfileUpsertRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        requireSmeAdminRole(jwt);
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        return ResponseEntity.ok(
                ProducerProfileResponse.from(producerProfileService.upsert(tenantId, request)));
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void requireSmeAdminRole(Jwt jwt) {
        JwtUtil.requireRole(jwt, "SME admin access required", "SME_ADMIN");
    }

}
