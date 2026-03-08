package hu.riskguard.identity.domain;

import hu.riskguard.identity.api.dto.TenantResponse;
import hu.riskguard.identity.internal.IdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public Optional<User> findUserByEmail(String email) {
        return identityRepository.findUserByEmail(email);
    }

    @Transactional(readOnly = true)
    public boolean hasMandate(UUID userId, UUID tenantId) {
        return identityRepository.hasMandate(userId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> findMandatedTenants(UUID userId) {
        return identityRepository.findMandatedTenants(userId).stream()
                .map(TenantResponse::from)
                .toList();
    }
}
