package hu.riskguard.identity.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class Tenant {
    private UUID id;
    private String name;
    private String tier = "ALAP";
    private OffsetDateTime createdAt;
}
