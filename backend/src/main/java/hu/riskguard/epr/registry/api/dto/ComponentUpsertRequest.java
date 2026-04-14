package hu.riskguard.epr.registry.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import hu.riskguard.epr.registry.domain.ComponentUpsertCommand;
import hu.riskguard.epr.registry.domain.RecyclabilityGrade;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ComponentUpsertRequest(
        UUID id,
        @NotBlank @Size(max = 512) String materialDescription,
        @Pattern(regexp = "^[0-9]{8}$") String kfCode,
        @NotNull @DecimalMin("0") BigDecimal weightPerUnitKg,
        @NotNull @Min(0) Integer componentOrder,
        RecyclabilityGrade recyclabilityGrade,
        @DecimalMin("0") @DecimalMax("100") BigDecimal recycledContentPct,
        Boolean reusable,
        JsonNode substancesOfConcern,
        @Size(max = 256) String supplierDeclarationRef,
        // Story 9.3: AI classification provenance (nullable — backward-compatible)
        String classificationSource,
        String classificationStrategy,
        String classificationModelVersion
) {
    public static ComponentUpsertRequest from(ComponentUpsertCommand cmd) {
        return new ComponentUpsertRequest(
                cmd.id(), cmd.materialDescription(), cmd.kfCode(), cmd.weightPerUnitKg(),
                cmd.componentOrder(), cmd.recyclabilityGrade(), cmd.recycledContentPct(),
                cmd.reusable(), cmd.substancesOfConcern(), cmd.supplierDeclarationRef(),
                cmd.classificationSource(), cmd.classificationStrategy(), cmd.classificationModelVersion()
        );
    }

    public ComponentUpsertCommand toCommand() {
        return new ComponentUpsertCommand(
                id, materialDescription, kfCode, weightPerUnitKg, componentOrder,
                recyclabilityGrade, recycledContentPct, reusable, substancesOfConcern,
                supplierDeclarationRef, classificationSource, classificationStrategy, classificationModelVersion
        );
    }
}
