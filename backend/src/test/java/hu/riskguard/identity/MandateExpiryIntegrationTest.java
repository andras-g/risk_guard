package hu.riskguard.identity;

import hu.riskguard.identity.domain.Tenant;
import hu.riskguard.identity.internal.IdentityRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.jooq.DSLContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for mandate expiry enforcement.
 * Uses explicit @Testcontainers + @Container to comply with the project's "Real-DB Mandate" rule.
 * @ServiceConnection overrides application-test.yml datasource URL with the container's URL.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class MandateExpiryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private IdentityRepository identityRepository;

    @Test
    void shouldDenyAccessForExpiredMandate() {
        // Given: a user with an expired mandate
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID mandateId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantId)
                .set(TENANTS.NAME, "Expired Mandate Tenant")
                .set(TENANTS.TIER, "ALAP")
                .set(TENANTS.CREATED_AT, now)
                .execute();

        dsl.insertInto(USERS)
                .set(USERS.ID, userId)
                .set(USERS.TENANT_ID, tenantId)
                .set(USERS.EMAIL, "expired-mandate-" + UUID.randomUUID() + "@test.com")
                .set(USERS.NAME, "Expired Mandate User")
                .set(USERS.ROLE, "SME_ADMIN")
                .set(USERS.PREFERRED_LANGUAGE, "hu")
                .set(USERS.CREATED_AT, now)
                .execute();

        // Create an EXPIRED mandate (valid_to in the past)
        dsl.insertInto(TENANT_MANDATES)
                .set(TENANT_MANDATES.ID, mandateId)
                .set(TENANT_MANDATES.ACCOUNTANT_USER_ID, userId)
                .set(TENANT_MANDATES.TENANT_ID, tenantId)
                .set(TENANT_MANDATES.VALID_FROM, now.minusDays(30))
                .set(TENANT_MANDATES.VALID_TO, now.minusDays(1)) // Expired yesterday
                .set(TENANT_MANDATES.CREATED_AT, now)
                .execute();

        // When / Then: expired mandate should not grant access
        boolean hasMandate = identityRepository.hasMandate(userId, tenantId);
        assertThat(hasMandate).isFalse();

        // And: expired mandates should not appear in findMandatedTenants
        List<Tenant> tenants = identityRepository.findMandatedTenants(userId);
        assertThat(tenants).isEmpty();
    }

    @Test
    void shouldAllowAccessForActiveMandate() {
        // Given: a user with an active mandate (valid_to in the future)
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID mandateId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantId)
                .set(TENANTS.NAME, "Active Mandate Tenant")
                .set(TENANTS.TIER, "ALAP")
                .set(TENANTS.CREATED_AT, now)
                .execute();

        dsl.insertInto(USERS)
                .set(USERS.ID, userId)
                .set(USERS.TENANT_ID, tenantId)
                .set(USERS.EMAIL, "active-mandate-" + UUID.randomUUID() + "@test.com")
                .set(USERS.NAME, "Active Mandate User")
                .set(USERS.ROLE, "SME_ADMIN")
                .set(USERS.PREFERRED_LANGUAGE, "hu")
                .set(USERS.CREATED_AT, now)
                .execute();

        // Create an ACTIVE mandate (valid_to in the future)
        dsl.insertInto(TENANT_MANDATES)
                .set(TENANT_MANDATES.ID, mandateId)
                .set(TENANT_MANDATES.ACCOUNTANT_USER_ID, userId)
                .set(TENANT_MANDATES.TENANT_ID, tenantId)
                .set(TENANT_MANDATES.VALID_FROM, now.minusDays(30))
                .set(TENANT_MANDATES.VALID_TO, now.plusDays(30)) // Active for another 30 days
                .set(TENANT_MANDATES.CREATED_AT, now)
                .execute();

        // When / Then: active mandate should grant access
        boolean hasMandate = identityRepository.hasMandate(userId, tenantId);
        assertThat(hasMandate).isTrue();

        // And: active mandates should appear in findMandatedTenants
        List<Tenant> tenants = identityRepository.findMandatedTenants(userId);
        assertThat(tenants).hasSize(1);
        assertThat(tenants.getFirst().getId()).isEqualTo(tenantId);
    }

    @Test
    void shouldAllowAccessForIndefiniteMandate() {
        // Given: a user with an indefinite mandate (valid_to is NULL)
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID mandateId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantId)
                .set(TENANTS.NAME, "Indefinite Mandate Tenant")
                .set(TENANTS.TIER, "ALAP")
                .set(TENANTS.CREATED_AT, now)
                .execute();

        dsl.insertInto(USERS)
                .set(USERS.ID, userId)
                .set(USERS.TENANT_ID, tenantId)
                .set(USERS.EMAIL, "indefinite-mandate-" + UUID.randomUUID() + "@test.com")
                .set(USERS.NAME, "Indefinite Mandate User")
                .set(USERS.ROLE, "SME_ADMIN")
                .set(USERS.PREFERRED_LANGUAGE, "hu")
                .set(USERS.CREATED_AT, now)
                .execute();

        // Create an INDEFINITE mandate (valid_to is NULL — never expires)
        dsl.insertInto(TENANT_MANDATES)
                .set(TENANT_MANDATES.ID, mandateId)
                .set(TENANT_MANDATES.ACCOUNTANT_USER_ID, userId)
                .set(TENANT_MANDATES.TENANT_ID, tenantId)
                .set(TENANT_MANDATES.VALID_FROM, now.minusDays(30))
                // valid_to deliberately not set → NULL
                .set(TENANT_MANDATES.CREATED_AT, now)
                .execute();

        // When / Then: indefinite mandate should grant access
        boolean hasMandate = identityRepository.hasMandate(userId, tenantId);
        assertThat(hasMandate).isTrue();

        // And: indefinite mandates should appear in findMandatedTenants
        List<Tenant> tenants = identityRepository.findMandatedTenants(userId);
        assertThat(tenants).hasSize(1);
    }
}
