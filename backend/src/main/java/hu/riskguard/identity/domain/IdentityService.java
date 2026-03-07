package hu.riskguard.identity.domain;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.identity.api.dto.TenantResponse;
import hu.riskguard.identity.internal.IdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service facade for identity operations.
 * Exposes IdentityRepository methods through the domain layer,
 * so API controllers don't directly depend on internal package.
 */
@Service
@RequiredArgsConstructor
public class IdentityService {

    private final IdentityRepository identityRepository;

    public Optional<User> findUserByEmail(String email) {
        return identityRepository.findUserByEmail(email);
    }

    public boolean hasMandate(UUID userId, UUID tenantId) {
        return identityRepository.hasMandate(userId, tenantId);
    }

    public List<TenantResponse> findMandatedTenants(UUID userId) {
        return identityRepository.findMandatedTenants(userId);
    }
}
