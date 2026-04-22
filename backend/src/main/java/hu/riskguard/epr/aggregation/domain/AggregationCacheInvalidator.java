package hu.riskguard.epr.aggregation.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Cache-invalidation facade for {@link InvoiceDrivenFilingAggregator}.
 *
 * <p>Story 10.11 AC #6a: every write that mutates {@code products.epr_scope} — single-product PATCH,
 * bulk-PATCH, demo-reset packaging endpoint — MUST call {@link #invalidateTenant(UUID)} so the next
 * filing-page aggregation fetch runs fresh. Skipping this step would leave the user staring at a
 * pre-reclassification result and re-training their "I clicked but nothing happened" reflex.
 *
 * <p>The indirection (service → invalidator → aggregator) keeps {@code RegistryService} free of a
 * direct compile-time dependency on the aggregation module, preserving layered-by-feature boundaries.
 * A dedicated bean also gives the unit-test suite a single Mockito interaction-assertion target
 * (AC #6a: "1 unit test per affected service method asserts the invalidator is called exactly once").
 */
@Component
@RequiredArgsConstructor
public class AggregationCacheInvalidator {

    private final InvoiceDrivenFilingAggregator aggregator;

    /**
     * Drop all cached aggregation results for a tenant. No-op when {@code tenantId} is null.
     */
    public void invalidateTenant(UUID tenantId) {
        aggregator.invalidateTenant(tenantId);
    }
}
