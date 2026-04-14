package hu.riskguard.epr.registry.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command object for creating or updating a single packaging component.
 * {@code id} is null for new components; non-null for existing ones being updated.
 */
public record ComponentUpsertCommand(
        UUID id,
        String materialDescription,
        String kfCode,
        BigDecimal weightPerUnitKg,
        int componentOrder,
        RecyclabilityGrade recyclabilityGrade,
        BigDecimal recycledContentPct,
        Boolean reusable,
        JsonNode substancesOfConcern,
        String supplierDeclarationRef
) {}
