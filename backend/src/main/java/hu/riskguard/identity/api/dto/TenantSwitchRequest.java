package hu.riskguard.identity.api.dto;

import java.util.UUID;

public record TenantSwitchRequest(UUID tenantId) {
    public static TenantSwitchRequest from(UUID tenantId) {
        return new TenantSwitchRequest(tenantId);
    }
}
