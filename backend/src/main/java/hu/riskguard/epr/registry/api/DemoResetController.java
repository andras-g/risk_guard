package hu.riskguard.epr.registry.api;

import hu.riskguard.core.security.Tier;
import hu.riskguard.core.security.TierRequired;
import hu.riskguard.core.util.JwtUtil;
import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.epr.aggregation.domain.AggregationCacheInvalidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.PRODUCTS;
import static hu.riskguard.jooq.Tables.PRODUCT_PACKAGING_COMPONENTS;

/**
 * Story 10.11 AC #15b — demo-only "reset packaging" endpoint.
 *
 * <p>Lets the Demo Accountant wipe {@code product_packaging_components} for one of two whitelisted
 * demo tenants, so Gemini can re-classify live during a presentation. Gated by
 * {@code @Profile({"demo","e2e"})} so the route does not exist under the production profile
 * (404 instead of 403) — verified by a dedicated controller-slice test.
 *
 * <p>Tenant-whitelist: only the two demo-producer tenants (taxNumber ∈ {99887766, 55667788}) may
 * call this. Any other tenant → 403 with {@code errorMessageKey=registry.demo.resetPackaging.notADemoTenant}.
 */
@RestController
@RequestMapping("/api/v1/registry/demo")
@RequiredArgsConstructor
@Slf4j
@Profile({"demo", "e2e"})
@TierRequired(Tier.PRO_EPR)
public class DemoResetController {

    private static final Set<String> DEMO_TAX_NUMBERS = Set.of("99887766", "55667788");

    private final DSLContext dsl;
    private final DataSourceService dataSourceService;
    private final AggregationCacheInvalidator aggregationCacheInvalidator;

    @PostMapping("/reset-packaging")
    @Transactional
    public ResponseEntity<Map<String, Integer>> resetPackaging(@AuthenticationPrincipal Jwt jwt) {
        JwtUtil.requireRole(jwt,
                "Demo reset requires SME_ADMIN, ACCOUNTANT, or PLATFORM_ADMIN role",
                "SME_ADMIN", "ACCOUNTANT", "PLATFORM_ADMIN");
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        UUID userId = JwtUtil.requireUuidClaim(jwt, "user_id");

        String taxNumber = dataSourceService.getTenantTaxNumber(tenantId).orElse(null);
        if (taxNumber == null || !DEMO_TAX_NUMBERS.contains(taxNumber)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "registry.demo.resetPackaging.notADemoTenant");
        }

        // Snapshot the distinct product IDs that have at least one component. We count "affected
        // products" as the size of this set — NOT the total product count for the tenant (review P11).
        List<UUID> productIdsWithComponents = dsl
                .selectDistinct(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID)
                .from(PRODUCT_PACKAGING_COMPONENTS)
                .join(PRODUCTS).on(PRODUCTS.ID.eq(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID))
                .where(PRODUCTS.TENANT_ID.eq(tenantId))
                .fetch(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID);

        int deletedComponents = productIdsWithComponents.isEmpty() ? 0
                : dsl.deleteFrom(PRODUCT_PACKAGING_COMPONENTS)
                        .where(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID.in(productIdsWithComponents))
                        .execute();

        int affectedProducts = productIdsWithComponents.size();

        aggregationCacheInvalidator.invalidateTenant(tenantId);

        try (MDC.MDCCloseable ignored = MDC.putCloseable("tenant", tenantId.toString())) {
            log.info("Demo packaging reset by user {} on tenant {}: deleted {} components across {} products",
                    userId, tenantId, deletedComponents, affectedProducts);
        }

        return ResponseEntity.ok(Map.of(
                "deletedComponents", deletedComponents,
                "affectedProducts", affectedProducts));
    }
}
