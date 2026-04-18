package hu.riskguard.epr.registry.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single packaging component in a product's bill-of-materials.
 *
 * <p>PPWR-ready nullable fields ({@code recyclabilityGrade}, {@code recycledContentPct},
 * {@code reusable}, {@code substancesOfConcern}, {@code supplierDeclarationRef}) are not
 * required today; they are collected for PPWR (Regulation 2025/40) readiness.
 *
 * <p>Epic 10 fields:
 * <ul>
 *   <li>{@code wrappingLevel} — 1=primary, 2=secondary/collector, 3=tertiary/transport. Drives
 *       OKIRkapu aggregation layers in Story 10.5.</li>
 *   <li>{@code materialTemplateId} — nullable FK into the internal material-template library.
 *       Picker-only access enforced at the frontend (Story 10.1 AC #12) and via
 *       ON DELETE RESTRICT at the DB layer.</li>
 *   <li>{@code itemsPerParent} — {@link BigDecimal} ratio of this component per one unit of its
 *       parent in the packaging hierarchy; widened from INT in Epic 9 to support fractional
 *       ratios (e.g., 0.5 half-pallet covers).</li>
 * </ul>
 */
public record ProductPackagingComponent(
        UUID id,
        UUID productId,
        String materialDescription,
        String kfCode,
        BigDecimal weightPerUnitKg,
        int componentOrder,
        BigDecimal itemsPerParent,
        int wrappingLevel,
        UUID materialTemplateId,
        RecyclabilityGrade recyclabilityGrade,
        BigDecimal recycledContentPct,
        Boolean reusable,
        JsonNode substancesOfConcern,
        String supplierDeclarationRef,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
