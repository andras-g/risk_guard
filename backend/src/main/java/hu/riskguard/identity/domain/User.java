package hu.riskguard.identity.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, unique = true)
    private String email;

    private String name;

    @Column(nullable = false)
    private String role = "SME_ADMIN";

    @Column(name = "preferred_language", nullable = false)
    private String preferredLanguage = "hu";

    @Column(name = "sso_provider")
    private String ssoProvider;

    @Column(name = "sso_subject")
    private String ssoSubject;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
