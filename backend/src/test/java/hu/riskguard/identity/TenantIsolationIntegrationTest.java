package hu.riskguard.identity;

import hu.riskguard.core.security.TenantContext;
import hu.riskguard.jooq.tables.records.TenantsRecord;
import hu.riskguard.jooq.tables.records.UsersRecord;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.GUEST_SESSIONS;
import static hu.riskguard.jooq.Tables.TENANTS;
import static hu.riskguard.jooq.Tables.USERS;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class TenantIsolationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private DSLContext dsl;

    @Test
    void shouldOnlySeeOwnTenantData() {
        // Given
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        // Create tenants via jOOQ Records to trigger listeners
        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantA)
                .set(TENANTS.NAME, "Tenant A")
                .set(TENANTS.TIER, "ALAP")
                .set(TENANTS.CREATED_AT, now)
                .execute();
        
        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantB)
                .set(TENANTS.NAME, "Tenant B")
                .set(TENANTS.TIER, "ALAP")
                .set(TENANTS.CREATED_AT, now)
                .execute();

        // When: Acting as Tenant A
        TenantContext.setCurrentTenant(tenantA);
        UsersRecord userA = dsl.newRecord(USERS);
        userA.setId(UUID.randomUUID());
        userA.setEmail("userA@test.com");
        userA.setName("User A");
        userA.setRole("SME_ADMIN");
        userA.setPreferredLanguage("hu");
        userA.setCreatedAt(now);
        userA.store();

        // When: Acting as Tenant B
        TenantContext.setCurrentTenant(tenantB);
        UsersRecord userB = dsl.newRecord(USERS);
        userB.setId(UUID.randomUUID());
        userB.setEmail("userB@test.com");
        userB.setName("User B");
        userB.setRole("SME_ADMIN");
        userB.setPreferredLanguage("hu");
        userB.setCreatedAt(now);
        userB.store();

        // Then: Acting as Tenant A
        TenantContext.setCurrentTenant(tenantA);
        int countA = dsl.fetchCount(USERS);

        // Then: Acting as Tenant B
        TenantContext.setCurrentTenant(tenantB);
        int countB = dsl.fetchCount(USERS);

        assertEquals(1, countA, "Tenant A should see exactly 1 user (auto-filtered)");
        assertEquals(1, countB, "Tenant B should see exactly 1 user (auto-filtered)");
        
        TenantContext.clear();
    }

    @Test
    void authenticatedUserShouldNeverSeeGuestSessionData() {
        // Given — create a guest session with a synthetic tenant ID
        UUID guestSessionId = UUID.randomUUID();
        UUID syntheticTenantId = UUID.nameUUIDFromBytes(("guest-" + guestSessionId).getBytes());
        OffsetDateTime now = OffsetDateTime.now();

        dsl.insertInto(GUEST_SESSIONS)
                .set(GUEST_SESSIONS.ID, guestSessionId)
                .set(GUEST_SESSIONS.TENANT_ID, syntheticTenantId)
                .set(GUEST_SESSIONS.SESSION_FINGERPRINT, "test-fingerprint")
                .set(GUEST_SESSIONS.COMPANIES_CHECKED, 3)
                .set(GUEST_SESSIONS.DAILY_CHECKS, 1)
                .set(GUEST_SESSIONS.CREATED_AT, now)
                .set(GUEST_SESSIONS.EXPIRES_AT, now.plusHours(24))
                .execute();

        // Given — create a real tenant and authenticated user
        UUID realTenantId = UUID.randomUUID();
        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, realTenantId)
                .set(TENANTS.NAME, "Real Tenant")
                .set(TENANTS.TIER, "ALAP")
                .set(TENANTS.CREATED_AT, now)
                .execute();

        // When — acting as the real tenant, query guest_sessions
        TenantContext.setCurrentTenant(realTenantId);
        int guestSessionCount = dsl.fetchCount(GUEST_SESSIONS);

        // Then — authenticated user should NOT see the guest session
        // (TenantJooqListener enforces tenant_id filtering on guest_sessions)
        assertEquals(0, guestSessionCount,
                "Authenticated user should never see guest session data " +
                "(synthetic tenant ID should prevent cross-tenant access)");

        TenantContext.clear();
    }

    @Test
    void shouldFailClosedWhenNoTenantContext() {
        // Given
        TenantContext.clear();

        // When/Then
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            dsl.fetchCount(USERS);
        }, "Should throw exception when no tenant context is set for tenant-aware table");
    }
}
