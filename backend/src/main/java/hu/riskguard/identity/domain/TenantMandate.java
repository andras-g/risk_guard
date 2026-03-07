package hu.riskguard.identity.domain;

import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class TenantMandate {
    private UUID id;
    private UUID accountantUserId;
    private UUID tenantId;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
}
