package hu.riskguard.epr.registry.bootstrap.api;

import hu.riskguard.core.security.Tier;
import hu.riskguard.core.security.TierRequired;
import hu.riskguard.core.util.JwtUtil;
import hu.riskguard.epr.registry.bootstrap.domain.BootstrapJobRecord;
import hu.riskguard.epr.registry.bootstrap.domain.BootstrapJobStatus;
import hu.riskguard.epr.registry.bootstrap.domain.BootstrapPreconditionException;
import hu.riskguard.epr.registry.bootstrap.domain.InvoiceDrivenRegistryBootstrapService;
import hu.riskguard.epr.registry.bootstrap.internal.BootstrapJobRepository;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for invoice-driven Registry bootstrap jobs (Story 10.4).
 *
 * <p>All endpoints require {@link Tier#PRO_EPR} tier (class-level) and
 * {@code role ∈ {SME_ADMIN, ACCOUNTANT, PLATFORM_ADMIN}} (method-level).
 * Tenant id is taken exclusively from JWT {@code active_tenant_id} — never from the body.
 */
@RestController
@RequestMapping("/api/v1/registry/bootstrap-from-invoices")
@TierRequired(Tier.PRO_EPR)
public class BootstrapJobController {

    private static final String BASE_PATH = "/api/v1/registry/bootstrap-from-invoices/";

    private final InvoiceDrivenRegistryBootstrapService bootstrapService;
    private final BootstrapJobRepository bootstrapJobRepository;

    public BootstrapJobController(InvoiceDrivenRegistryBootstrapService bootstrapService,
                                   BootstrapJobRepository bootstrapJobRepository) {
        this.bootstrapService = bootstrapService;
        this.bootstrapJobRepository = bootstrapJobRepository;
    }

    /**
     * Trigger a new bootstrap job.
     *
     * @return 202 Accepted with body {@link BootstrapJobCreatedResponse} and Location header
     */
    @PostMapping
    @Operation(summary = "Trigger invoice-driven Registry bootstrap",
               description = "Fetches last 3 months of NAV invoices, classifies packaging, and populates the Registry.")
    public ResponseEntity<BootstrapJobCreatedResponse> trigger(
            @RequestBody(required = false) BootstrapTriggerRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        JwtUtil.requireRole(jwt,
                "bootstrap requires SME_ADMIN, ACCOUNTANT, or PLATFORM_ADMIN",
                "SME_ADMIN", "ACCOUNTANT", "PLATFORM_ADMIN");
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        UUID actingUserId = JwtUtil.requireUuidClaim(jwt, "user_id");

        UUID jobId = bootstrapService.startJob(
                tenantId, actingUserId,
                request != null ? request.periodFrom() : null,
                request != null ? request.periodTo() : null
        );

        String location = BASE_PATH + jobId;
        return ResponseEntity.accepted()
                .location(URI.create(location))
                .body(new BootstrapJobCreatedResponse(jobId, location));
    }

    /**
     * Poll job status by id.
     *
     * @return 200 with {@link BootstrapJobStatusResponse}, 404 on unknown id, 403 on cross-tenant
     */
    @GetMapping("/{jobId}")
    @Operation(summary = "Get bootstrap job status")
    public BootstrapJobStatusResponse getStatus(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal Jwt jwt) {

        JwtUtil.requireRole(jwt,
                "bootstrap requires SME_ADMIN, ACCOUNTANT, or PLATFORM_ADMIN",
                "SME_ADMIN", "ACCOUNTANT", "PLATFORM_ADMIN");
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");

        BootstrapJobRecord record = loadJobOrThrow(jobId, tenantId);
        return BootstrapJobStatusResponse.from(record);
    }

    /**
     * Resolve a job while distinguishing unknown id (404) from cross-tenant access (403)
     * per AC #8 / AC #9. A single {@link BootstrapJobRepository#findByIdAndTenant} collapses
     * both cases into 404; the explicit two-step pattern below preserves the distinction.
     */
    private BootstrapJobRecord loadJobOrThrow(UUID jobId, UUID tenantId) {
        UUID owner = bootstrapJobRepository.findTenantForJob(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bootstrap job not found: " + jobId));
        if (!owner.equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bootstrap job does not belong to this tenant");
        }
        return bootstrapJobRepository.findByIdAndTenant(jobId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bootstrap job not found: " + jobId));
    }

    /**
     * Cancel an in-progress job.
     *
     * <p>The cancellation is done atomically via a conditional UPDATE (cancelIfActive):
     * on zero rows affected, we distinguish "unknown / cross-tenant" (404) from
     * "already terminal" (409) by a follow-up tenant-scoped read. This closes the
     * race where a worker transitions to a terminal state between a pre-read and
     * the UPDATE.
     *
     * @return 204 on success, 409 if already terminal, 404 on unknown, 403 on cross-tenant
     */
    @DeleteMapping("/{jobId}")
    @Operation(summary = "Cancel a bootstrap job")
    public ResponseEntity<Object> cancel(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal Jwt jwt) {

        JwtUtil.requireRole(jwt,
                "bootstrap requires SME_ADMIN, ACCOUNTANT, or PLATFORM_ADMIN",
                "SME_ADMIN", "ACCOUNTANT", "PLATFORM_ADMIN");
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");

        int updated = bootstrapJobRepository.cancelIfActive(jobId, tenantId);
        if (updated > 0) {
            return ResponseEntity.noContent().build();
        }

        // Zero rows — disambiguate unknown id (404), cross-tenant (403), and already-terminal (409).
        BootstrapJobRecord record = loadJobOrThrow(jobId, tenantId);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "ALREADY_TERMINAL", "status", record.status().name()));
    }

    /**
     * Renders structured {@code { code, message, ...extra }} bodies for 403/409/412
     * per AC #11/#12/#13 so the frontend can match error codes without string parsing.
     */
    @ExceptionHandler(BootstrapPreconditionException.class)
    public ResponseEntity<Map<String, Object>> handlePrecondition(BootstrapPreconditionException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ex.code());
        body.put("message", ex.getMessage());
        body.putAll(ex.extraProperties());
        return ResponseEntity.status(ex.status()).body(body);
    }
}
