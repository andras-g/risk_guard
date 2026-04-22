package hu.riskguard.epr.registry.api;

import hu.riskguard.core.security.Tier;
import hu.riskguard.core.security.TierRequired;
import hu.riskguard.core.util.JwtUtil;
import hu.riskguard.epr.audit.AuditSource;
import hu.riskguard.epr.registry.api.dto.*;
import hu.riskguard.epr.registry.domain.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for Product-Packaging Registry operations.
 * All endpoints require PRO_EPR tier. Tenant identity extracted from JWT claims.
 */
@RestController
@RequestMapping("/api/v1/registry")
@RequiredArgsConstructor
@TierRequired(Tier.PRO_EPR)
public class RegistryController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 50;

    private final RegistryService registryService;

    /**
     * Returns total non-archived products and how many have at least one kf_code component.
     * Result is cached per-tenant for 10 seconds (see RegistryService.summaryCache).
     */
    @GetMapping("/summary")
    public RegistrySummaryResponse summary(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        return RegistrySummaryResponse.from(registryService.getSummary(tenantId));
    }

    /**
     * List products with optional filters and server-side pagination.
     */
    @GetMapping
    public RegistryPageResponse list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String vtsz,
            @RequestParam(required = false) String kfCode,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) ReviewState reviewState,
            @RequestParam(required = false) AuditSource classifierSource,
            @RequestParam(required = false) Boolean onlyUnknownScope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        RegistryListFilter filter = new RegistryListFilter(q, vtsz, kfCode, status,
                reviewState, classifierSource, onlyUnknownScope);

        var summaries = registryService.list(tenantId, filter, clampedPage, clampedSize);
        long total = registryService.count(tenantId, filter);

        return RegistryPageResponse.from(
                summaries.stream().map(ProductSummaryResponse::from).toList(),
                total, clampedPage, clampedSize
        );
    }

    /**
     * Get a single product with full component list.
     */
    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id,
                                @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        return ProductResponse.from(registryService.get(tenantId, id));
    }

    /**
     * Create a new product with packaging components.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductUpsertRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        UUID actingUserId = JwtUtil.requireUuidClaim(jwt, "user_id");
        return ProductResponse.from(
                registryService.create(tenantId, actingUserId, request.toCommand())
        );
    }

    /**
     * Update an existing product and its components.
     */
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody ProductUpsertRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        UUID actingUserId = JwtUtil.requireUuidClaim(jwt, "user_id");
        return ProductResponse.from(
                registryService.update(tenantId, id, actingUserId, request.toCommand())
        );
    }

    /**
     * Archive a product (sets status = ARCHIVED).
     */
    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id,
                         @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        UUID actingUserId = JwtUtil.requireUuidClaim(jwt, "user_id");
        registryService.archive(tenantId, id, actingUserId);
    }

    /**
     * Story 10.11 AC #7 — PATCH a single product's {@code epr_scope}.
     *
     * <p>400 on invalid scope; 404 on cross-tenant / missing product; 409 when product is archived.
     * Idempotent: PATCH with the current scope returns 200 with no state change and no audit row.
     */
    @PatchMapping("/products/{id}/epr-scope")
    public ProductResponse updateEprScope(@PathVariable UUID id,
                                           @Valid @RequestBody UpdateEprScopeRequest request,
                                           @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        UUID actingUserId = JwtUtil.requireUuidClaim(jwt, "user_id");
        JwtUtil.requireRole(jwt, "PATCH epr-scope requires SME_ADMIN, ACCOUNTANT, or PLATFORM_ADMIN role",
                "SME_ADMIN", "ACCOUNTANT", "PLATFORM_ADMIN");
        return ProductResponse.from(
                registryService.updateProductScope(tenantId, id, actingUserId, request.scope()));
    }

    /**
     * Story 10.11 AC #8 — POST bulk scope update. Max 500 IDs; single transaction; all-or-nothing
     * on cross-tenant / missing IDs; idempotent rows returned as {@code skipped}.
     */
    @PostMapping("/products/bulk-epr-scope")
    public BulkEprScopeResponse bulkUpdateEprScope(@Valid @RequestBody BulkEprScopeRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        UUID actingUserId = JwtUtil.requireUuidClaim(jwt, "user_id");
        JwtUtil.requireRole(jwt, "Bulk scope update requires SME_ADMIN, ACCOUNTANT, or PLATFORM_ADMIN role",
                "SME_ADMIN", "ACCOUNTANT", "PLATFORM_ADMIN");
        return BulkEprScopeResponse.from(
                registryService.bulkUpdateProductScopes(tenantId, actingUserId,
                        request.productIds(), request.scope()));
    }

    /**
     * Get paginated audit log for a product (newest first).
     */
    @GetMapping("/{id}/audit-log")
    public RegistryAuditPageResponse auditLog(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        var entries = registryService.listAuditLog(tenantId, id, clampedPage, clampedSize);
        long total = registryService.countAuditLog(tenantId, id);

        return RegistryAuditPageResponse.from(
                entries.stream().map(RegistryAuditEntryResponse::from).toList(),
                total, clampedPage, clampedSize
        );
    }
}
