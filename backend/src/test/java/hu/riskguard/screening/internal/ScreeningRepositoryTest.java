package hu.riskguard.screening.internal;

import hu.riskguard.core.security.TenantContext;
import hu.riskguard.jooq.enums.VerdictConfidence;
import hu.riskguard.jooq.enums.VerdictStatus;
import hu.riskguard.screening.domain.AuditHistoryFilter;
import hu.riskguard.screening.internal.ScreeningRepository.AdminAuditHistoryRow;
import hu.riskguard.screening.internal.ScreeningRepository.AuditHistoryRow;
import hu.riskguard.screening.internal.ScreeningRepository.AuditVerifyRow;
import org.jooq.DSLContext;
import org.jooq.JSONB;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Integration tests for {@link ScreeningRepository} audit history query methods.
 * Uses Testcontainers (PostgreSQL 17) with Flyway migrations applied.
 *
 * <p>Covers:
 * <ul>
 *   <li>Pagination: findAuditHistoryPage / countAuditHistory</li>
 *   <li>Date filter, taxNumber filter, checkSource filter</li>
 *   <li>Tenant isolation: cross-tenant leakage prevention</li>
 *   <li>findAuditEntryForVerification: tenant-scoped hash input retrieval</li>
 * </ul>
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ScreeningRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private ScreeningRepository screeningRepository;

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
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantA).set(TENANTS.NAME, "Tenant A")
                .set(TENANTS.TIER, "ALAP").set(TENANTS.CREATED_AT, now).execute();
        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantB).set(TENANTS.NAME, "Tenant B")
                .set(TENANTS.TIER, "ALAP").set(TENANTS.CREATED_AT, now).execute();
        dsl.insertInto(USERS)
                .set(USERS.ID, userA).set(USERS.TENANT_ID, tenantA)
                .set(USERS.EMAIL, "a-" + suffix + "@test.com").set(USERS.NAME, "A")
                .set(USERS.ROLE, "SME_ADMIN").set(USERS.PREFERRED_LANGUAGE, "hu")
                .set(USERS.CREATED_AT, now).execute();
        dsl.insertInto(USERS)
                .set(USERS.ID, userB).set(USERS.TENANT_ID, tenantB)
                .set(USERS.EMAIL, "b-" + suffix + "@test.com").set(USERS.NAME, "B")
                .set(USERS.ROLE, "SME_ADMIN").set(USERS.PREFERRED_LANGUAGE, "hu")
                .set(USERS.CREATED_AT, now).execute();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── Pagination ──────────────────────────────────────────────────────────

    @Test
    void findAuditHistoryPage_returnsRowsForTenant_paginated() {
        // Given — 3 audit entries for tenantA, 1 for tenantB
        TenantContext.setCurrentTenant(tenantA);
        insertAuditRow(tenantA, userA, "11111111", "MANUAL", "LIVE", OffsetDateTime.now().minusDays(2));
        insertAuditRow(tenantA, userA, "22222222", "MANUAL", "DEMO", OffsetDateTime.now().minusDays(1));
        insertAuditRow(tenantA, userA, "33333333", "AUTOMATED", "LIVE", OffsetDateTime.now());
        insertAuditRow(tenantB, userB, "99999999", "MANUAL", "LIVE", OffsetDateTime.now());

        // When — page 0, size 2
        List<AuditHistoryRow> page0 = screeningRepository.findAuditHistoryPage(
                tenantA, null, 0, 2);
        List<AuditHistoryRow> page1 = screeningRepository.findAuditHistoryPage(
                tenantA, null, 2, 2);
        long total = screeningRepository.countAuditHistory(tenantA, null);

        // Then
        assertThat(total).isEqualTo(3);
        assertThat(page0).hasSize(2);
        assertThat(page1).hasSize(1);
        // Rows are ordered searched_at DESC — most recent first
        assertThat(page0.get(0).taxNumber()).isEqualTo("33333333");
        assertThat(page0.get(1).taxNumber()).isEqualTo("22222222");
    }

    // ─── Tenant Isolation ────────────────────────────────────────────────────

    @Test
    void findAuditHistoryPage_tenantIsolation_onlyOwnRecordsReturned() {
        // Given — both tenants have audit entries
        insertAuditRow(tenantA, userA, "12345678", "MANUAL", "LIVE", OffsetDateTime.now());
        insertAuditRow(tenantB, userB, "87654321", "MANUAL", "LIVE", OffsetDateTime.now());

        // When — query tenantA
        List<AuditHistoryRow> rowsA = screeningRepository.findAuditHistoryPage(tenantA, null, 0, 100);
        long countA = screeningRepository.countAuditHistory(tenantA, null);

        // When — query tenantB
        List<AuditHistoryRow> rowsB = screeningRepository.findAuditHistoryPage(tenantB, null, 0, 100);
        long countB = screeningRepository.countAuditHistory(tenantB, null);

        // Then — no cross-tenant leakage
        assertThat(rowsA).hasSize(1);
        assertThat(rowsA.get(0).taxNumber()).isEqualTo("12345678");
        assertThat(countA).isEqualTo(1);

        assertThat(rowsB).hasSize(1);
        assertThat(rowsB.get(0).taxNumber()).isEqualTo("87654321");
        assertThat(countB).isEqualTo(1);
    }

    // ─── Date filter ─────────────────────────────────────────────────────────

    @Test
    void findAuditHistoryPage_dateFilter_appliedCorrectly() {
        // Given — entries at different dates
        OffsetDateTime jan1 = OffsetDateTime.parse("2026-01-01T10:00:00Z");
        OffsetDateTime feb1 = OffsetDateTime.parse("2026-02-01T10:00:00Z");
        OffsetDateTime mar1 = OffsetDateTime.parse("2026-03-01T10:00:00Z");
        insertAuditRow(tenantA, userA, "11111111", "MANUAL", "LIVE", jan1);
        insertAuditRow(tenantA, userA, "22222222", "MANUAL", "LIVE", feb1);
        insertAuditRow(tenantA, userA, "33333333", "MANUAL", "LIVE", mar1);

        // When — filter: February only
        AuditHistoryFilter filter = new AuditHistoryFilter(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), null, null);
        List<AuditHistoryRow> rows = screeningRepository.findAuditHistoryPage(tenantA, filter, 0, 100);
        long count = screeningRepository.countAuditHistory(tenantA, filter);

        // Then
        assertThat(count).isEqualTo(1);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).taxNumber()).isEqualTo("22222222");
    }

    // ─── taxNumber filter ────────────────────────────────────────────────────

    @Test
    void findAuditHistoryPage_taxNumberFilter_exactMatch() {
        // Given
        insertAuditRow(tenantA, userA, "12345678", "MANUAL", "LIVE", OffsetDateTime.now().minusDays(1));
        insertAuditRow(tenantA, userA, "99887766", "MANUAL", "LIVE", OffsetDateTime.now());

        // When
        AuditHistoryFilter filter = new AuditHistoryFilter(null, null, "12345678", null);
        List<AuditHistoryRow> rows = screeningRepository.findAuditHistoryPage(tenantA, filter, 0, 100);
        long count = screeningRepository.countAuditHistory(tenantA, filter);

        // Then
        assertThat(count).isEqualTo(1);
        assertThat(rows.get(0).taxNumber()).isEqualTo("12345678");
    }

    // ─── checkSource filter ──────────────────────────────────────────────────

    @Test
    void findAuditHistoryPage_checkSourceFilter_automated() {
        // Given — mixed manual and automated entries
        insertAuditRow(tenantA, userA, "11111111", "MANUAL", "LIVE", OffsetDateTime.now().minusDays(1));
        insertAuditRow(tenantA, userA, "22222222", "AUTOMATED", "LIVE", OffsetDateTime.now());
        insertAuditRow(tenantA, userA, "33333333", "AUTOMATED", "DEMO", OffsetDateTime.now());

        // When — filter AUTOMATED only
        AuditHistoryFilter filter = new AuditHistoryFilter(null, null, null, "AUTOMATED");
        List<AuditHistoryRow> rows = screeningRepository.findAuditHistoryPage(tenantA, filter, 0, 100);
        long count = screeningRepository.countAuditHistory(tenantA, filter);

        // Then
        assertThat(count).isEqualTo(2);
        assertThat(rows).extracting(AuditHistoryRow::checkSource)
                .containsOnly("AUTOMATED");
    }

    // ─── findAuditEntryForVerification ───────────────────────────────────────

    @Test
    void findAuditEntryForVerification_tenantOwner_returnsRow() {
        // Given
        TenantContext.setCurrentTenant(tenantA);
        UUID snapshotId = createSnapshot(tenantA, "12345678");
        UUID verdictId = screeningRepository.createVerdict(
                snapshotId, VerdictStatus.RELIABLE, VerdictConfidence.FRESH, OffsetDateTime.now());
        String hash = screeningRepository.writeAuditLog(
                "12345678", userA, "Disclaimer", "{}", "RELIABLE", "FRESH",
                verdictId, OffsetDateTime.now(), "MANUAL", "LIVE");

        // Get the audit id
        UUID auditId = dsl.select(field("id", UUID.class))
                .from(table("search_audit_log"))
                .where(field("verdict_id", UUID.class).eq(verdictId))
                .fetchOne(field("id", UUID.class));

        // When
        Optional<AuditVerifyRow> result = screeningRepository.findAuditEntryForVerification(auditId, tenantA);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().storedHash()).isEqualTo(hash);
        assertThat(result.get().verdictStatus()).isEqualTo("RELIABLE");
    }

    @Test
    void findAuditEntryForVerification_wrongTenant_returnsEmpty() {
        // Given — entry belongs to tenantA
        TenantContext.setCurrentTenant(tenantA);
        UUID snapshotId = createSnapshot(tenantA, "12345678");
        UUID verdictId = screeningRepository.createVerdict(
                snapshotId, VerdictStatus.RELIABLE, VerdictConfidence.FRESH, OffsetDateTime.now());
        screeningRepository.writeAuditLog(
                "12345678", userA, "Disclaimer", "{}", "RELIABLE", "FRESH",
                verdictId, OffsetDateTime.now(), "MANUAL", "LIVE");

        UUID auditId = dsl.select(field("id", UUID.class))
                .from(table("search_audit_log"))
                .where(field("verdict_id", UUID.class).eq(verdictId))
                .fetchOne(field("id", UUID.class));

        // When — tenantB tries to access tenantA's audit entry
        Optional<AuditVerifyRow> result = screeningRepository.findAuditEntryForVerification(auditId, tenantB);

        // Then — empty (tenant isolation enforced)
        assertThat(result).isEmpty();
    }

    // ─── Admin Audit Query Tests (Story 6.4) ────────────────────────────────

    @Test
    void findAdminAuditPage_byTaxNumber_returnsAcrossAllTenants() {
        // Use unique tax numbers to avoid collisions with rows from other tests in the shared DB
        String uniqueTax = "A" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        String otherTax  = "B" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);

        // Given — same tax number searched by two different tenants
        insertAuditRow(tenantA, userA, uniqueTax, "MANUAL", "DEMO", OffsetDateTime.now().minusHours(2));
        insertAuditRow(tenantB, userB, uniqueTax, "AUTOMATED", "DEMO", OffsetDateTime.now().minusHours(1));
        insertAuditRow(tenantA, userA, otherTax, "MANUAL", "DEMO", OffsetDateTime.now());

        // When — admin queries by taxNumber (cross-tenant, no tenantId filter)
        List<AdminAuditHistoryRow> rows = screeningRepository.findAdminAuditPage(uniqueTax, null, 0, 100);

        // Then — both tenants' rows are returned
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(AdminAuditHistoryRow::taxNumber).containsOnly(uniqueTax);
        assertThat(rows).extracting(AdminAuditHistoryRow::tenantId)
                .containsExactlyInAnyOrder(tenantA, tenantB);
    }

    @Test
    void findAdminAuditPage_byTenantId_returnsOnlyThatTenant() {
        String tax1 = "T1" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String tax2 = "T2" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String tax3 = "T3" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);

        // Given
        insertAuditRow(tenantA, userA, tax1, "MANUAL", "DEMO", OffsetDateTime.now().minusHours(1));
        insertAuditRow(tenantA, userA, tax2, "MANUAL", "DEMO", OffsetDateTime.now());
        insertAuditRow(tenantB, userB, tax3, "MANUAL", "DEMO", OffsetDateTime.now());

        // When
        List<AdminAuditHistoryRow> rows = screeningRepository.findAdminAuditPage(null, tenantA, 0, 100);

        // Then — only tenantA's rows; use hasSizeGreaterThanOrEqualTo because the shared
        // Testcontainer DB accumulates rows across test methods (other tests also insert for tenantA).
        assertThat(rows).hasSizeGreaterThanOrEqualTo(2);
        assertThat(rows).extracting(AdminAuditHistoryRow::taxNumber).contains(tax1, tax2);
        assertThat(rows).extracting(AdminAuditHistoryRow::tenantId).containsOnly(tenantA);
    }

    @Test
    void findAdminAuditPage_combined_filtersByBothCriteria() {
        String sharedTax = "C" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        String otherTax  = "D" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);

        // Given — tenantA and tenantB both have sharedTax
        insertAuditRow(tenantA, userA, sharedTax, "MANUAL", "DEMO", OffsetDateTime.now().minusHours(1));
        insertAuditRow(tenantB, userB, sharedTax, "MANUAL", "DEMO", OffsetDateTime.now());
        insertAuditRow(tenantA, userA, otherTax, "MANUAL", "DEMO", OffsetDateTime.now());

        // When — admin queries by both taxNumber AND tenantId
        List<AdminAuditHistoryRow> rows = screeningRepository.findAdminAuditPage(sharedTax, tenantA, 0, 100);

        // Then — only the intersection (tenantA + sharedTax)
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).taxNumber()).isEqualTo(sharedTax);
        assertThat(rows.get(0).tenantId()).isEqualTo(tenantA);
    }

    @Test
    void findAdminAuditPage_noMatchingRows_returnsEmptyList() {
        String noMatchTax = "Z" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);

        // When — no rows were inserted for this tax number
        List<AdminAuditHistoryRow> rows = screeningRepository.findAdminAuditPage(noMatchTax, null, 0, 100);

        // Then
        assertThat(rows).isEmpty();
    }

    @Test
    void countAdminAudit_byTaxNumber_countsAcrossAllTenants() {
        String uniqueTax = "E" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        String otherTax  = "F" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);

        // Given — two tenants each with one row for the same unique tax number
        insertAuditRow(tenantA, userA, uniqueTax, "MANUAL", "DEMO", OffsetDateTime.now().minusHours(1));
        insertAuditRow(tenantB, userB, uniqueTax, "MANUAL", "DEMO", OffsetDateTime.now());
        insertAuditRow(tenantA, userA, otherTax, "MANUAL", "DEMO", OffsetDateTime.now());

        // When
        long count = screeningRepository.countAdminAudit(uniqueTax, null);

        // Then — both tenants counted (cross-tenant)
        assertThat(count).isEqualTo(2);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void insertAuditRow(UUID tenantId, UUID userId, String taxNumber,
                                 String checkSource, String dataSourceMode, OffsetDateTime searchedAt) {
        dsl.insertInto(table("search_audit_log"))
                .set(field("id", UUID.class), UUID.randomUUID())
                .set(field("tenant_id", UUID.class), tenantId)
                .set(field("tax_number", String.class), taxNumber)
                .set(field("searched_by", UUID.class), userId)
                .set(field("sha256_hash", String.class), UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""))
                .set(field("disclaimer_text", String.class), "Test disclaimer")
                .set(field("searched_at", OffsetDateTime.class), searchedAt)
                .set(field("check_source", String.class), checkSource)
                .set(field("data_source_mode", String.class), dataSourceMode)
                .execute();
    }

    private UUID createSnapshot(UUID tenantId, String taxNumber) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(COMPANY_SNAPSHOTS)
                .set(COMPANY_SNAPSHOTS.ID, id)
                .set(COMPANY_SNAPSHOTS.TENANT_ID, tenantId)
                .set(COMPANY_SNAPSHOTS.TAX_NUMBER, taxNumber)
                .set(COMPANY_SNAPSHOTS.SNAPSHOT_DATA, JSONB.jsonb("{}"))
                .set(COMPANY_SNAPSHOTS.CREATED_AT, now)
                .set(COMPANY_SNAPSHOTS.UPDATED_AT, now)
                .execute();
        return id;
    }
}
