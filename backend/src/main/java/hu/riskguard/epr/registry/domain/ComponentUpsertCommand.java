package hu.riskguard.epr.registry.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command object for creating or updating a single packaging component.
 * {@code id} is null for new components; non-null for existing ones being updated.
 *
 * <p>{@code classificationSource}, {@code classificationStrategy}, and {@code classificationModelVersion}
 * are nullable fields added in Story 9.3 to carry AI provenance from the frontend through to
 * {@code registry_entry_audit_log}. All existing callers may pass {@code null}.
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
        String supplierDeclarationRef,
        // Story 9.3: AI classification provenance (nullable — backward-compatible)
        String classificationSource,
        String classificationStrategy,
        String classificationModelVersion
) {}
