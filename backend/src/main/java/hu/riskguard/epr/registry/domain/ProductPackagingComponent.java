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
 */
public record ProductPackagingComponent(
        UUID id,
        UUID productId,
        String materialDescription,
        String kfCode,
        BigDecimal weightPerUnitKg,
        int componentOrder,
        int unitsPerProduct,
        RecyclabilityGrade recyclabilityGrade,
        BigDecimal recycledContentPct,
        Boolean reusable,
        JsonNode substancesOfConcern,
        String supplierDeclarationRef,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
