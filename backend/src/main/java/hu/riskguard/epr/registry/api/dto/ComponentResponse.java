package hu.riskguard.epr.registry.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import hu.riskguard.epr.registry.domain.ProductPackagingComponent;
import hu.riskguard.epr.registry.domain.RecyclabilityGrade;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ComponentResponse(
        UUID id,
        UUID productId,
        String materialDescription,
        String kfCode,
        BigDecimal weightPerUnitKg,
        int componentOrder,
        RecyclabilityGrade recyclabilityGrade,
        BigDecimal recycledContentPct,
        Boolean reusable,
        JsonNode substancesOfConcern,
        String supplierDeclarationRef,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ComponentResponse from(ProductPackagingComponent c) {
        return new ComponentResponse(
                c.id(), c.productId(), c.materialDescription(), c.kfCode(),
                c.weightPerUnitKg(), c.componentOrder(), c.recyclabilityGrade(),
                c.recycledContentPct(), c.reusable(), c.substancesOfConcern(),
                c.supplierDeclarationRef(), c.createdAt(), c.updatedAt()
        );
    }
}
