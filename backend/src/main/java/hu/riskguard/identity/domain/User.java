package hu.riskguard.identity.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class User {
    private UUID id;
    private UUID tenantId;
    private String email;
    private String name;
    private String role = "SME_ADMIN";
    private String preferredLanguage = "hu";
    private String ssoProvider;
    private String ssoSubject;
    private OffsetDateTime createdAt;
}
