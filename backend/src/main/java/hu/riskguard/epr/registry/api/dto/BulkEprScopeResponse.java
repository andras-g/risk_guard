package hu.riskguard.epr.registry.api.dto;

import hu.riskguard.epr.registry.domain.RegistryService.BulkScopeResult;

/**
 * Response for {@code POST /api/v1/registry/products/bulk-epr-scope} — Story 10.11 AC #8.
 *
 * @param updated count of products whose scope actually changed
 * @param skipped count of products already at the target scope (idempotent hits)
 */
public record BulkEprScopeResponse(int updated, int skipped) {

    public static BulkEprScopeResponse from(BulkScopeResult r) {
        return new BulkEprScopeResponse(r.updated(), r.skipped());
    }
}
