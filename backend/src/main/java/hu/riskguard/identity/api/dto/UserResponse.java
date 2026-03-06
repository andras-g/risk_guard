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
        UUID activeTenantId
) {
    public static UserResponse from(User user, String activeTenantId) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getPreferredLanguage(),
                user.getTenantId(),
                activeTenantId != null ? UUID.fromString(activeTenantId) : user.getTenantId()
        );
    }
}
