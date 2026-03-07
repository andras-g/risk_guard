package hu.riskguard.identity.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TenantSwitchRequest(@NotNull UUID tenantId) {
    public static TenantSwitchRequest from(UUID tenantId) {
        return new TenantSwitchRequest(tenantId);
    }
}
