package hu.riskguard.identity.api.dto;

import hu.riskguard.identity.domain.Tenant;
import java.util.UUID;

public record TenantResponse(UUID id, String name, String tier) {
    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(tenant.getId(), tenant.getName(), tenant.getTier());
    }
}
