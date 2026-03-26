package hu.riskguard.identity.api.dto;

import hu.riskguard.identity.domain.User;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String name,
        String role,
        String preferredLanguage,
        UUID homeTenantId,
        UUID activeTenantId,
        String tier
) {
    public static UserResponse from(User user, String activeTenantId, String tier) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getPreferredLanguage(),
                user.getTenantId(),
                activeTenantId != null ? UUID.fromString(activeTenantId) : user.getTenantId(),
                tier
        );
    }
}
