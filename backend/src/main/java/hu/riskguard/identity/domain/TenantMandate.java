package hu.riskguard.identity.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_mandates")
@Getter
@Setter
public class TenantMandate {
    @Id
    private UUID id;

    @Column(name = "accountant_user_id", nullable = false)
    private UUID accountantUserId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "valid_from", nullable = false)
    private OffsetDateTime validFrom = OffsetDateTime.now();

    @Column(name = "valid_to")
    private OffsetDateTime validTo;
}
