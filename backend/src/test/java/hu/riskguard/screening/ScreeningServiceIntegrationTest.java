package hu.riskguard.screening;

import hu.riskguard.core.security.TenantContext;
import hu.riskguard.core.util.HashUtil;
import hu.riskguard.screening.api.dto.VerdictResponse;
import hu.riskguard.screening.domain.ScreeningService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import static hu.riskguard.jooq.Tables.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ScreeningServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private ScreeningService screeningService;

    @Autowired
    private DSLContext dsl;

    private UUID tenantA;
    private UUID tenantB;
    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setUp() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        // Use unique emails per test run to avoid duplicate key violations across shared context
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // Create tenants
        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantA).set(TENANTS.NAME, "Tenant A")
                .set(TENANTS.TIER, "ALAP").set(TENANTS.CREATED_AT, now).execute();
        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantB).set(TENANTS.NAME, "Tenant B")
                .set(TENANTS.TIER, "ALAP").set(TENANTS.CREATED_AT, now).execute();

        // Create users (bypass tenant auto-filter for setup)
        dsl.insertInto(USERS)
                .set(USERS.ID, userA).set(USERS.TENANT_ID, tenantA)
                .set(USERS.EMAIL, "userA-" + uniqueSuffix + "@test.com").set(USERS.NAME, "User A")
                .set(USERS.ROLE, "SME_ADMIN").set(USERS.PREFERRED_LANGUAGE, "hu")
                .set(USERS.CREATED_AT, now).execute();
        dsl.insertInto(USERS)
                .set(USERS.ID, userB).set(USERS.TENANT_ID, tenantB)
                .set(USERS.EMAIL, "userB-" + uniqueSuffix + "@test.com").set(USERS.NAME, "User B")
                .set(USERS.ROLE, "SME_ADMIN").set(USERS.PREFERRED_LANGUAGE, "hu")
                .set(USERS.CREATED_AT, now).execute();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void searchShouldCreateSnapshotVerdictAndAuditLog() {
        // Given
        TenantContext.setCurrentTenant(tenantA);
        String taxNumber = "12345678";

        // When
        VerdictResponse response = screeningService.search(taxNumber, userA, tenantA);

        // Then — VerdictResponse returned with INCOMPLETE status
        assertThat(response).isNotNull();
        assertThat(response.taxNumber()).isEqualTo(taxNumber);
        assertThat(response.status()).isEqualTo("INCOMPLETE");
        assertThat(response.confidence()).isEqualTo("UNAVAILABLE");
        assertThat(response.snapshotId()).isNotNull();
        assertThat(response.id()).isNotNull();

        // Verify snapshot was created in DB
        int snapshotCount = dsl.select().from(COMPANY_SNAPSHOTS)
                .where(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantA))
                .and(COMPANY_SNAPSHOTS.TAX_NUMBER.eq(taxNumber))
                .execute();
        assertThat(snapshotCount).isEqualTo(1);

        // Verify verdict was created in DB
        int verdictCount = dsl.select().from(VERDICTS)
                .where(VERDICTS.TENANT_ID.eq(tenantA))
                .and(VERDICTS.SNAPSHOT_ID.eq(response.snapshotId()))
                .execute();
        assertThat(verdictCount).isEqualTo(1);

        // Verify audit log was created with SHA-256 hash
        var auditRecord = dsl.select().from(SEARCH_AUDIT_LOG)
                .where(SEARCH_AUDIT_LOG.TENANT_ID.eq(tenantA))
                .and(SEARCH_AUDIT_LOG.TAX_NUMBER.eq(taxNumber))
                .fetchOne();
        assertThat(auditRecord).isNotNull();

        String hash = auditRecord.get(SEARCH_AUDIT_LOG.SHA256_HASH);
        assertThat(hash).isNotBlank();
        assertThat(hash).hasSize(64); // SHA-256 hex = 64 chars

        // Verify hash integrity — recompute and compare
        String disclaimerText = auditRecord.get(SEARCH_AUDIT_LOG.DISCLAIMER_TEXT);
        String expectedHash = HashUtil.sha256(tenantA.toString(), taxNumber, disclaimerText);
        assertThat(hash).isEqualTo(expectedHash);
    }

    @Test
    void searchShouldReturnCachedResultForFreshSnapshot() {
        // Given — first search creates snapshot
        TenantContext.setCurrentTenant(tenantA);
        String taxNumber = "12345678";
        VerdictResponse first = screeningService.search(taxNumber, userA, tenantA);

        // When — second search within 15 min should return cached result
        VerdictResponse second = screeningService.search(taxNumber, userA, tenantA);

        // Then — same verdict returned (idempotency guard)
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.snapshotId()).isEqualTo(first.snapshotId());

        // Only 1 snapshot should exist (no duplicate)
        int snapshotCount = dsl.select().from(COMPANY_SNAPSHOTS)
                .where(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantA))
                .and(COMPANY_SNAPSHOTS.TAX_NUMBER.eq(taxNumber))
                .execute();
        assertThat(snapshotCount).isEqualTo(1);
    }

    @Test
    void searchShouldIsolateTenantData() {
        // Given — Tenant A searches for a tax number
        TenantContext.setCurrentTenant(tenantA);
        String taxNumber = "12345678";
        screeningService.search(taxNumber, userA, tenantA);

        // When — Tenant B searches for the same tax number
        TenantContext.setCurrentTenant(tenantB);
        VerdictResponse tenantBResult = screeningService.search(taxNumber, userB, tenantB);

        // Then — Tenant B should get its own snapshot (not see Tenant A's data)
        assertThat(tenantBResult).isNotNull();

        // Verify each tenant has exactly 1 snapshot
        int countA = dsl.select().from(COMPANY_SNAPSHOTS)
                .where(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantA))
                .and(COMPANY_SNAPSHOTS.TAX_NUMBER.eq(taxNumber))
                .execute();
        int countB = dsl.select().from(COMPANY_SNAPSHOTS)
                .where(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantB))
                .and(COMPANY_SNAPSHOTS.TAX_NUMBER.eq(taxNumber))
                .execute();

        assertThat(countA).isEqualTo(1);
        assertThat(countB).isEqualTo(1);
    }

    @Test
    void searchShouldNormalizeTaxNumberWithHyphens() {
        // Given
        TenantContext.setCurrentTenant(tenantA);

        // When — search with hyphenated format
        VerdictResponse response = screeningService.search("1234-5678", userA, tenantA);

        // Then — stored as normalized (no hyphens)
        assertThat(response.taxNumber()).isEqualTo("12345678");
    }
}
