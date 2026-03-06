package hu.riskguard.identity.api.dto;

public record TenantSwitchResponse(String token) {
    public static TenantSwitchResponse from(String token) {
        return new TenantSwitchResponse(token);
    }
}
