package hu.riskguard.epr.api;

import hu.riskguard.core.security.Tier;
import hu.riskguard.core.security.TierRequired;
import hu.riskguard.epr.api.dto.*;
import hu.riskguard.epr.domain.EprService;
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

import java.util.List;
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

    private final EprService eprService;

    /**
     * Create a new material template.
     */
    @PostMapping("/materials")
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialTemplateResponse createTemplate(
            @Valid @RequestBody MaterialTemplateRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = requireUuidClaim(jwt, "active_tenant_id");
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
        UUID tenantId = requireUuidClaim(jwt, "active_tenant_id");
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

        UUID tenantId = requireUuidClaim(jwt, "active_tenant_id");
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

        UUID tenantId = requireUuidClaim(jwt, "active_tenant_id");
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

        UUID tenantId = requireUuidClaim(jwt, "active_tenant_id");
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

        UUID tenantId = requireUuidClaim(jwt, "active_tenant_id");
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
        UUID tenantId = requireUuidClaim(jwt, "active_tenant_id");
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
        UUID tenantId = requireUuidClaim(jwt, "active_tenant_id");
        return eprService.retryLink(request.calculationId(), request.templateId(), tenantId);
    }

    /**
     * Compute EPR filing liability for a set of material quantities.
     * Validates all templates are Verified and belong to the requesting tenant.
     */
    @PostMapping("/filing/calculate")
    public FilingCalculationResponse calculateFiling(
            @Valid @RequestBody FilingCalculationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = requireUuidClaim(jwt, "active_tenant_id");
        return eprService.calculateFiling(request.lines(), tenantId);
    }

    /**
     * Generate a MOHU-compliant CSV export for the completed EPR filing and download it.
     * Logs the export in {@code epr_exports} with SHA-256 file hash.
     */
    @PostMapping("/filing/export")
    public ResponseEntity<byte[]> exportMohu(
            @Valid @RequestBody MohuExportRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = requireUuidClaim(jwt, "active_tenant_id");
        byte[] csv = eprService.exportMohuCsv(request, tenantId);
        String filename = "mohu-epr-" + java.time.LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv);
    }

    /**
     * Extract and validate a UUID claim from the JWT.
     */
    private UUID requireUuidClaim(Jwt jwt, String claimName) {
        String claimValue = jwt.getClaimAsString(claimName);
        if (claimValue == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing " + claimName + " claim in JWT");
        }
        try {
            return UUID.fromString(claimValue);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid " + claimName + " claim in JWT: not a valid UUID");
        }
    }
}
