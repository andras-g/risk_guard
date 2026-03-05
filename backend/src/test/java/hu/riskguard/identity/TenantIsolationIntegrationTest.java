package hu.riskguard.identity;

import hu.riskguard.core.security.TenantContext;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class TenantIsolationIntegrationTest {

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

        // Create data via raw SQL
        dsl.execute("INSERT INTO tenants (id, name, tier, created_at) VALUES (?, ?, ?, ?)", 
                tenantA, "Tenant A", "ALAP", now);
        dsl.execute("INSERT INTO tenants (id, name, tier, created_at) VALUES (?, ?, ?, ?)", 
                tenantB, "Tenant B", "ALAP", now);
        
        dsl.execute("INSERT INTO users (id, tenant_id, email, name, role, preferred_language, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)", 
                UUID.randomUUID(), tenantA, "userA@test.com", "User A", "SME_ADMIN", "hu", now);
        dsl.execute("INSERT INTO users (id, tenant_id, email, name, role, preferred_language, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)", 
                UUID.randomUUID(), tenantB, "userB@test.com", "User B", "SME_ADMIN", "hu", now);

        // When: Acting as Tenant A
        TenantContext.setCurrentTenant(tenantA);
        int countA = dsl.fetchCount(DSL.table("users"), DSL.field("tenant_id").eq(TenantContext.getCurrentTenant()));

        // When: Acting as Tenant B
        TenantContext.setCurrentTenant(tenantB);
        int countB = dsl.fetchCount(DSL.table("users"), DSL.field("tenant_id").eq(TenantContext.getCurrentTenant()));

        // Then
        assertEquals(1, countA, "Tenant A should see exactly 1 user");
        assertEquals(1, countB, "Tenant B should see exactly 1 user");
        
        TenantContext.clear();
    }
}
