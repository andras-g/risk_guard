package hu.riskguard.identity.domain;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.identity.api.dto.TenantResponse;
import hu.riskguard.identity.internal.IdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service facade for identity operations.
 * Exposes IdentityRepository methods through the domain layer,
 * so API controllers don't directly depend on internal package.
 *
 * <p>All read methods are annotated {@code @Transactional(readOnly = true)} to:
 * <ul>
 *   <li>Ensure consistent snapshot reads (no phantom reads between multiple queries in one request).</li>
 *   <li>Prevent TOCTOU issues (e.g., user deleted between {@code findUserByEmail} and {@code hasMandate}).</li>
 *   <li>Allow the JPA/JDBC layer to optimize for read-only connections.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class IdentityService {

    private final IdentityRepository identityRepository;
    private final PasswordEncoder passwordEncoder;
    private final RiskGuardProperties properties;

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return identityRepository.existsByEmail(email);
    }

    @Transactional(readOnly = true)
    public Optional<String> findSsoProviderByEmail(String email) {
        return identityRepository.findSsoProviderByEmail(email);
    }

    /**
     * Register a new local (email/password) user.
     * Creates a tenant + user + self-mandate, mirroring the SSO provisioning flow.
     */
    @Transactional
    public User registerLocalUser(String email, String password, String name) {
        OffsetDateTime now = OffsetDateTime.now();

        // Create Tenant
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName(name + "'s Tenant");
        tenant.setTier(properties.getIdentity().getDefaultTier());
        tenant.setCreatedAt(now);
        identityRepository.saveTenant(tenant);

        // Create User
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenant.getId());
        user.setEmail(email);
        user.setName(name);
        user.setSsoProvider("local");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(properties.getIdentity().getDefaultUserRole());
        user.setPreferredLanguage(properties.getIdentity().getDefaultLanguage());
        user.setCreatedAt(now);
        User savedUser = identityRepository.saveUser(user);

        // Create Initial Mandate (Self-access)
        TenantMandate mandate = new TenantMandate();
        mandate.setId(UUID.randomUUID());
        mandate.setAccountantUserId(savedUser.getId());
        mandate.setTenantId(tenant.getId());
        mandate.setValidFrom(now);
        identityRepository.saveTenantMandate(mandate);

        return savedUser;
    }

    @Transactional(readOnly = true)
    public Optional<User> findUserByEmail(String email) {
        return identityRepository.findUserByEmail(email);
    }

    @Transactional(readOnly = true)
    public boolean hasMandate(UUID userId, UUID tenantId) {
        return identityRepository.hasMandate(userId, tenantId);
    }

    @Transactional
    public void updatePreferredLanguage(UUID userId, String language) {
        identityRepository.updatePreferredLanguage(userId, language);
    }

    /**
     * Returns the tier string for a tenant, or null if not found.
     * Used by TierGateInterceptor for tier enforcement.
     */
    @Transactional(readOnly = true)
    public String findTenantTier(UUID tenantId) {
        return identityRepository.findTenantTier(tenantId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> findMandatedTenants(UUID userId) {
        return identityRepository.findMandatedTenants(userId).stream()
                .map(TenantResponse::from)
                .toList();
    }
}
