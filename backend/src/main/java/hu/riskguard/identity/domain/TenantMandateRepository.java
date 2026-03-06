package hu.riskguard.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TenantMandateRepository extends JpaRepository<TenantMandate, UUID> {
    List<TenantMandate> findByAccountantUserId(UUID accountantUserId);
    boolean existsByAccountantUserIdAndTenantId(UUID accountantUserId, UUID tenantId);
}
