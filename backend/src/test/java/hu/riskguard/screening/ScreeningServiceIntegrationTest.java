package hu.riskguard.screening;

import hu.riskguard.core.security.TenantContext;
import hu.riskguard.core.util.HashUtil;
import hu.riskguard.jooq.enums.VerdictConfidence;
import hu.riskguard.jooq.enums.VerdictStatus;
import hu.riskguard.datasource.api.dto.CompanyData;
import hu.riskguard.datasource.api.dto.ScrapedData;
import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.screening.api.dto.ProvenanceResponse;
import hu.riskguard.screening.domain.ScreeningService;
import hu.riskguard.screening.domain.ScreeningService.SearchResult;
import hu.riskguard.screening.domain.events.PartnerStatusChanged;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import hu.riskguard.screening.internal.ScreeningRepository;

import static hu.riskguard.jooq.Tables.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@RecordApplicationEvents
class ScreeningServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private DataSourceService dataSourceService;

    @Autowired
    private ScreeningService screeningService;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private ApplicationEvents applicationEvents;

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

        // Mock DataSourceService to return clean test data — all sources available, no risk signals
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildCleanCompanyData());

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
        SearchResult response = screeningService.search(taxNumber, userA, tenantA);

        // Then — SearchResult returned with RELIABLE status (clean data, all sources available)
        assertThat(response).isNotNull();
        assertThat(response.taxNumber()).isEqualTo(taxNumber);
        assertThat(response.status()).isEqualTo(VerdictStatus.RELIABLE);
        assertThat(response.confidence()).isEqualTo(VerdictConfidence.FRESH);
        assertThat(response.snapshotId()).isNotNull();
        assertThat(response.verdictId()).isNotNull();
        assertThat(response.riskSignals()).isNotNull().isEmpty();

        // Verify snapshot was created in DB with scraped data
        var snapshot = dsl.selectFrom(COMPANY_SNAPSHOTS)
                .where(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantA))
                .and(COMPANY_SNAPSHOTS.TAX_NUMBER.eq(taxNumber))
                .fetchOne();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.get(COMPANY_SNAPSHOTS.SNAPSHOT_DATA).data()).isNotEqualTo("{}");
        assertThat(snapshot.get(COMPANY_SNAPSHOTS.SOURCE_URLS).data()).contains("http://nav/test");
        assertThat(snapshot.get(COMPANY_SNAPSHOTS.DOM_FINGERPRINT_HASH)).isEqualTo("abc123hash");
        assertThat(snapshot.get(COMPANY_SNAPSHOTS.CHECKED_AT)).isNotNull();

        // Verify verdict was created in DB
        int verdictCount = dsl.selectCount().from(VERDICTS)
                .where(VERDICTS.TENANT_ID.eq(tenantA))
                .and(VERDICTS.SNAPSHOT_ID.eq(response.snapshotId()))
                .fetchOne(0, int.class);
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
        assertThat(hash).matches("[0-9a-f]{64}"); // lowercase hex

        // Verify hash matches what ScreeningService returned (audit log stores the same hash as SearchResult)
        assertThat(hash).isEqualTo(response.sha256Hash());

        // Verify hash is NOT the old formula (tenantId + taxNumber + disclaimer) — Story 2.5 changed this
        String oldFormulaHash = HashUtil.sha256(tenantA.toString(), taxNumber,
                auditRecord.get(SEARCH_AUDIT_LOG.DISCLAIMER_TEXT));
        assertThat(hash).isNotEqualTo(oldFormulaHash);

        // Verify verdict_id FK is populated in audit log
        UUID auditVerdictId = auditRecord.get(SEARCH_AUDIT_LOG.VERDICT_ID);
        assertThat(auditVerdictId).isNotNull();
        assertThat(auditVerdictId).isEqualTo(response.verdictId());
    }

    @Test
    void searchShouldReturnCachedResultForFreshSnapshot() {
        // Given — first search creates snapshot
        TenantContext.setCurrentTenant(tenantA);
        String taxNumber = "12345678";
        SearchResult first = screeningService.search(taxNumber, userA, tenantA);

        // When — second search within 15 min should return cached result
        SearchResult second = screeningService.search(taxNumber, userA, tenantA);

        // Then — same verdict returned (idempotency guard)
        assertThat(second.verdictId()).isEqualTo(first.verdictId());
        assertThat(second.snapshotId()).isEqualTo(first.snapshotId());

        // Only 1 snapshot should exist (no duplicate)
        int snapshotCount = dsl.selectCount().from(COMPANY_SNAPSHOTS)
                .where(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantA))
                .and(COMPANY_SNAPSHOTS.TAX_NUMBER.eq(taxNumber))
                .fetchOne(0, int.class);
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
        SearchResult tenantBResult = screeningService.search(taxNumber, userB, tenantB);

        // Then — Tenant B should get its own snapshot (not see Tenant A's data)
        assertThat(tenantBResult).isNotNull();

        // Verify each tenant has exactly 1 snapshot
        int countA = dsl.selectCount().from(COMPANY_SNAPSHOTS)
                .where(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantA))
                .and(COMPANY_SNAPSHOTS.TAX_NUMBER.eq(taxNumber))
                .fetchOne(0, int.class);
        int countB = dsl.selectCount().from(COMPANY_SNAPSHOTS)
                .where(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantB))
                .and(COMPANY_SNAPSHOTS.TAX_NUMBER.eq(taxNumber))
                .fetchOne(0, int.class);

        assertThat(countA).isEqualTo(1);
        assertThat(countB).isEqualTo(1);
    }

    @Test
    void searchShouldNormalizeTaxNumberWithHyphens() {
        // Given
        TenantContext.setCurrentTenant(tenantA);

        // When — search with hyphenated format
        SearchResult response = screeningService.search("1234-5678", userA, tenantA);

        // Then — stored as normalized (no hyphens)
        assertThat(response.taxNumber()).isEqualTo("12345678");
    }

    @Test
    void searchWithDebtDataShouldReturnAtRiskVerdict() {
        // Given — mock returns data with public debt
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildDebtCompanyData());

        // When
        SearchResult response = screeningService.search("12345678", userA, tenantA);

        // Then
        assertThat(response.status()).isEqualTo(VerdictStatus.AT_RISK);
        assertThat(response.confidence()).isEqualTo(VerdictConfidence.FRESH);
        assertThat(response.riskSignals()).isNotEmpty();
        assertThat(response.riskSignals()).contains("PUBLIC_DEBT_DETECTED");
    }

    @Test
    void searchWithPartialFailureShouldReturnIncompleteVerdict() {
        // Given — mock returns data with nav-debt source unavailable
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildPartialFailureCompanyData());

        // When
        SearchResult response = screeningService.search("12345678", userA, tenantA);

        // Then
        assertThat(response.status()).isEqualTo(VerdictStatus.INCOMPLETE);
        assertThat(response.confidence()).isEqualTo(VerdictConfidence.FRESH);
        assertThat(response.riskSignals()).isNotEmpty();
    }

    // --- Event publishing tests (Story 2-3.5) ---

    @Test
    void shouldNotPublishStatusChangedEventOnFirstSearch() {
        // Given — first-ever search for this tax number (no previous verdict exists)
        TenantContext.setCurrentTenant(tenantA);

        // When
        screeningService.search("11111111", userA, tenantA);

        // Then — no PartnerStatusChanged event (first search establishes baseline)
        long statusChangedCount = applicationEvents.stream(PartnerStatusChanged.class).count();
        assertThat(statusChangedCount).isZero();
    }

    @Test
    void shouldNotPublishStatusChangedEventWhenStatusUnchanged() {
        // Given — first search with clean data → RELIABLE
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildCleanCompanyData());

        // First search — wait for idempotency guard to expire by using a different tax number
        screeningService.search("22222222", userA, tenantA);

        // Expire the idempotency guard by backdating the snapshot
        dsl.update(COMPANY_SNAPSHOTS)
                .set(COMPANY_SNAPSHOTS.CREATED_AT, OffsetDateTime.now().minusMinutes(20))
                .set(COMPANY_SNAPSHOTS.CHECKED_AT, OffsetDateTime.now().minusMinutes(20))
                .where(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantA))
                .and(COMPANY_SNAPSHOTS.TAX_NUMBER.eq("22222222"))
                .execute();

        // Clear recorded events from first search
        applicationEvents.clear();

        // When — second search with same clean data → still RELIABLE (no change)
        screeningService.search("22222222", userA, tenantA);

        // Then — no PartnerStatusChanged event (status unchanged: RELIABLE → RELIABLE)
        long statusChangedCount = applicationEvents.stream(PartnerStatusChanged.class).count();
        assertThat(statusChangedCount).isZero();
    }

    @Test
    void shouldPublishStatusChangedEventWhenStatusDiffers() {
        // Given — first search with clean data → RELIABLE
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildCleanCompanyData());
        screeningService.search("33333333", userA, tenantA);

        // Expire the idempotency guard
        dsl.update(COMPANY_SNAPSHOTS)
                .set(COMPANY_SNAPSHOTS.CREATED_AT, OffsetDateTime.now().minusMinutes(20))
                .set(COMPANY_SNAPSHOTS.CHECKED_AT, OffsetDateTime.now().minusMinutes(20))
                .where(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantA))
                .and(COMPANY_SNAPSHOTS.TAX_NUMBER.eq("33333333"))
                .execute();

        // Clear recorded events from first search
        applicationEvents.clear();

        // When — second search with debt data → AT_RISK (status changed!)
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildDebtCompanyData());
        SearchResult result = screeningService.search("33333333", userA, tenantA);

        // Then — PartnerStatusChanged event published with correct status transition
        List<PartnerStatusChanged> events = applicationEvents.stream(PartnerStatusChanged.class).toList();
        assertThat(events).hasSize(1);

        PartnerStatusChanged event = events.getFirst();
        assertThat(event.previousStatus()).isEqualTo("RELIABLE");
        assertThat(event.newStatus()).isEqualTo("AT_RISK");
        assertThat(event.verdictId()).isEqualTo(result.verdictId());
        assertThat(event.tenantId()).isEqualTo(tenantA);
    }

    @Test
    void shouldNotPublishStatusChangedEventForDifferentTenantSameTaxNumber() {
        // Given — Tenant A searches with clean data → RELIABLE
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildCleanCompanyData());
        screeningService.search("44444444", userA, tenantA);

        // Clear recorded events from Tenant A's first search
        applicationEvents.clear();

        // When — Tenant B searches for same tax number with debt data → AT_RISK
        // This is Tenant B's FIRST search, so no status change event should fire
        TenantContext.setCurrentTenant(tenantB);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildDebtCompanyData());
        screeningService.search("44444444", userB, tenantB);

        // Then — no PartnerStatusChanged event (Tenant B has no previous verdict)
        long statusChangedCount = applicationEvents.stream(PartnerStatusChanged.class).count();
        assertThat(statusChangedCount).isZero();
    }

    @Test
    void statusChangedEventShouldBePublishedAfterTx2Commits() {
        // Given — first search to establish baseline RELIABLE verdict
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildCleanCompanyData());
        screeningService.search("55555555", userA, tenantA);

        // Expire idempotency guard
        dsl.update(COMPANY_SNAPSHOTS)
                .set(COMPANY_SNAPSHOTS.CREATED_AT, OffsetDateTime.now().minusMinutes(20))
                .set(COMPANY_SNAPSHOTS.CHECKED_AT, OffsetDateTime.now().minusMinutes(20))
                .where(COMPANY_SNAPSHOTS.TENANT_ID.eq(tenantA))
                .and(COMPANY_SNAPSHOTS.TAX_NUMBER.eq("55555555"))
                .execute();

        applicationEvents.clear();

        // When — second search triggers status change RELIABLE → AT_RISK
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildDebtCompanyData());
        SearchResult result = screeningService.search("55555555", userA, tenantA);

        // Then — the verdict referenced by the event must actually exist in the DB
        // (proving the event was published after TX2 committed)
        List<PartnerStatusChanged> events = applicationEvents.stream(PartnerStatusChanged.class).toList();
        assertThat(events).hasSize(1);

        UUID eventVerdictId = events.getFirst().verdictId();
        int verdictExists = dsl.selectCount().from(VERDICTS)
                .where(VERDICTS.ID.eq(eventVerdictId))
                .and(VERDICTS.TENANT_ID.eq(tenantA))
                .fetchOne(0, int.class);
        assertThat(verdictExists).isEqualTo(1);
    }

    @Test
    void searchWithSuspendedTaxShouldReturnTaxSuspendedVerdict() {
        // Given — mock returns data with TAX_SUSPENDED status
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildSuspendedCompanyData());

        // When
        SearchResult response = screeningService.search("12345678", userA, tenantA);

        // Then
        assertThat(response.status()).isEqualTo(VerdictStatus.TAX_SUSPENDED);
        assertThat(response.confidence()).isEqualTo(VerdictConfidence.FRESH);
        assertThat(response.riskSignals()).isNotEmpty();
        assertThat(response.riskSignals()).contains("TAX_NUMBER_SUSPENDED");
    }

    // --- Demo adapter format tests (Story 2.2.2 review follow-up) ---

    @Test
    void searchWithDemoAdapterFormatShouldReturnReliableVerdict() {
        // Given — mock returns demo adapter single-key format (as DemoCompanyDataAdapter produces)
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildDemoCleanCompanyData());

        // When
        SearchResult response = screeningService.search("12345678", userA, tenantA);

        // Then — RELIABLE verdict from single "demo" adapter key format
        assertThat(response.status()).isEqualTo(VerdictStatus.RELIABLE);
        assertThat(response.confidence()).isEqualTo(VerdictConfidence.FRESH);
        assertThat(response.riskSignals()).isNotNull().isEmpty();
    }

    @Test
    void searchWithDemoAdapterDebtFormatShouldReturnAtRiskVerdict() {
        // Given — mock returns demo adapter format with public debt
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildDemoDebtCompanyData());

        // When
        SearchResult response = screeningService.search("11223344", userA, tenantA);

        // Then — AT_RISK verdict from demo adapter with hasPublicDebt=true
        assertThat(response.status()).isEqualTo(VerdictStatus.AT_RISK);
        assertThat(response.riskSignals()).isNotEmpty();
        assertThat(response.riskSignals()).contains("PUBLIC_DEBT_DETECTED");
    }

    // --- Provenance tests (Story 2.4) ---

    @Test
    void getSnapshotProvenanceShouldReturnSourceAvailabilityFromSnapshotJsonb() {
        // Given — search creates a snapshot with demo adapter data
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildDemoCleanCompanyData());
        SearchResult result = screeningService.search("12345678", userA, tenantA);

        // When — fetch provenance for the snapshot
        Optional<ProvenanceResponse> provenance = screeningService.getSnapshotProvenance(result.snapshotId());

        // Then — provenance returned with demo adapter source
        assertThat(provenance).isPresent();
        assertThat(provenance.get().snapshotId()).isEqualTo(result.snapshotId());
        assertThat(provenance.get().taxNumber()).isEqualTo("12345678");
        assertThat(provenance.get().sources()).isNotEmpty();

        // Demo adapter should be AVAILABLE; demo has no public URL (internal adapter)
        ProvenanceResponse.SourceProvenance demoSource = provenance.get().sources().stream()
                .filter(s -> "demo".equals(s.sourceName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected 'demo' source in provenance"));
        assertThat(demoSource.available()).isTrue();
        assertThat(demoSource.sourceUrl()).isNull(); // demo adapter has no public URL
    }

    @Test
    void getSnapshotProvenanceShouldPopulateKnownAdapterSourceUrls() {
        // Given — search creates a snapshot with multi-adapter data (nav-debt, e-cegjegyzek, cegkozlony)
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildCleanCompanyData());
        SearchResult result = screeningService.search("12345678", userA, tenantA);

        // When — fetch provenance
        Optional<ProvenanceResponse> provenance = screeningService.getSnapshotProvenance(result.snapshotId());

        // Then — known adapters have canonical source URLs populated
        assertThat(provenance).isPresent();

        ProvenanceResponse.SourceProvenance navDebt = provenance.get().sources().stream()
                .filter(s -> "nav-debt".equals(s.sourceName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected 'nav-debt' source in provenance"));
        assertThat(navDebt.sourceUrl()).isEqualTo("https://nav.gov.hu/ellenorzesi-adatbazisok/nav-online-adatbazis");

        ProvenanceResponse.SourceProvenance cegjegyzek = provenance.get().sources().stream()
                .filter(s -> "e-cegjegyzek".equals(s.sourceName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected 'e-cegjegyzek' source in provenance"));
        assertThat(cegjegyzek.sourceUrl()).isEqualTo("https://e-cegjegyzek.hu");
    }

    @Test
    void getSnapshotProvenanceShouldHandlePartialFailureData() {
        // Given — search creates a snapshot with partial failure (nav-debt unavailable)
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildPartialFailureCompanyData());
        SearchResult result = screeningService.search("12345678", userA, tenantA);

        // When — fetch provenance
        Optional<ProvenanceResponse> provenance = screeningService.getSnapshotProvenance(result.snapshotId());

        // Then — nav-debt shows as unavailable
        assertThat(provenance).isPresent();

        ProvenanceResponse.SourceProvenance navDebt = provenance.get().sources().stream()
                .filter(s -> "nav-debt".equals(s.sourceName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected 'nav-debt' source in provenance"));
        assertThat(navDebt.available()).isFalse();

        ProvenanceResponse.SourceProvenance cegjegyzek = provenance.get().sources().stream()
                .filter(s -> "e-cegjegyzek".equals(s.sourceName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected 'e-cegjegyzek' source in provenance"));
        assertThat(cegjegyzek.available()).isTrue();
    }

    @Test
    void getSnapshotProvenanceShouldReturnEmptyForUnknownSnapshot() {
        // Given
        TenantContext.setCurrentTenant(tenantA);
        UUID nonExistentSnapshotId = UUID.randomUUID();

        // When
        Optional<ProvenanceResponse> provenance = screeningService.getSnapshotProvenance(nonExistentSnapshotId);

        // Then — empty result (not found)
        assertThat(provenance).isEmpty();
    }

    @Test
    void getSnapshotProvenanceShouldRespectTenantIsolation() {
        // Given — Tenant A searches, creating a snapshot
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildDemoCleanCompanyData());
        SearchResult result = screeningService.search("12345678", userA, tenantA);

        // When — Tenant B tries to access Tenant A's snapshot
        TenantContext.setCurrentTenant(tenantB);
        Optional<ProvenanceResponse> provenanceForTenantB = screeningService.getSnapshotProvenance(result.snapshotId());

        // Then — Tenant B cannot see Tenant A's snapshot (tenant isolation)
        assertThat(provenanceForTenantB).isEmpty();
    }

    @Test
    void searchShouldReturnCompanyNameAndSha256HashInResult() {
        // Given — demo data includes companyName
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildDemoCleanCompanyData());

        // When
        SearchResult result = screeningService.search("12345678", userA, tenantA);

        // Then — companyName extracted from demo adapter JSONB
        assertThat(result.companyName()).isNotNull();
        assertThat(result.companyName()).isEqualTo("Példa Kereskedelmi Kft.");

        // And sha256Hash is populated from the audit log (Story 2.5: new hash formula)
        assertThat(result.sha256Hash()).isNotNull();
        assertThat(result.sha256Hash()).hasSize(64);
    }

    @Test
    void searchShouldStoreCorrectVerdictIdFkInAuditLog() {
        // Given
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildDemoCleanCompanyData());

        // When
        SearchResult result = screeningService.search("99887766", userA, tenantA);

        // Then — verdict_id FK in audit log matches the returned verdictId
        assertThat(result.sha256Hash()).isNotNull();
        assertThat(result.sha256Hash()).hasSize(64);

        UUID auditVerdictId = dsl.select(SEARCH_AUDIT_LOG.VERDICT_ID)
                .from(SEARCH_AUDIT_LOG)
                .where(SEARCH_AUDIT_LOG.TENANT_ID.eq(tenantA))
                .and(SEARCH_AUDIT_LOG.TAX_NUMBER.eq("99887766"))
                .fetchOne(SEARCH_AUDIT_LOG.VERDICT_ID);
        assertThat(auditVerdictId).isNotNull();
        assertThat(auditVerdictId).isEqualTo(result.verdictId());
    }

    @Test
    void searchShouldUseNewHashFormulaSnapshotPlusVerdictPlusDisclaimer() {
        // Given
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildDemoCleanCompanyData());

        // When
        SearchResult result = screeningService.search("55443322", userA, tenantA);

        // Then — hash stored in audit log matches the hash returned in SearchResult
        var auditRecord = dsl.select().from(SEARCH_AUDIT_LOG)
                .where(SEARCH_AUDIT_LOG.TENANT_ID.eq(tenantA))
                .and(SEARCH_AUDIT_LOG.TAX_NUMBER.eq("55443322"))
                .fetchOne();
        assertThat(auditRecord).isNotNull();

        String storedHash = auditRecord.get(SEARCH_AUDIT_LOG.SHA256_HASH);
        assertThat(storedHash).hasSize(64);
        assertThat(storedHash).matches("[0-9a-f]{64}");

        // The stored hash in the audit log must equal what SearchResult returned
        assertThat(storedHash).isEqualTo(result.sha256Hash());

        // The hash must NOT be the old formula (tenantId + taxNumber + disclaimer)
        String oldFormulaHash = HashUtil.sha256(
                tenantA.toString(), "55443322", auditRecord.get(SEARCH_AUDIT_LOG.DISCLAIMER_TEXT));
        assertThat(storedHash).isNotEqualTo(oldFormulaHash);
    }

    @Test
    void cachedSearchShouldReturnNullSha256Hash() {
        // Given — first search creates audit log entry
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildDemoCleanCompanyData());
        SearchResult first = screeningService.search("77665544", userA, tenantA);
        assertThat(first.sha256Hash()).isNotNull();

        // When — second search within idempotency window (cached)
        SearchResult second = screeningService.search("77665544", userA, tenantA);

        // Then — cached result has null sha256Hash (no new audit log written)
        assertThat(second.cached()).isTrue();
        assertThat(second.sha256Hash()).isNull();

        // Only 1 audit log row (first search only)
        int auditCount = dsl.selectCount().from(SEARCH_AUDIT_LOG)
                .where(SEARCH_AUDIT_LOG.TENANT_ID.eq(tenantA))
                .and(SEARCH_AUDIT_LOG.TAX_NUMBER.eq("77665544"))
                .fetchOne(0, int.class);
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void searchWithNullSnapshotDataShouldWriteHashUnavailableSentinelToAuditLog() {
        // Given — DataSourceService returns CompanyData with null snapshotData map.
        // This exercises the null-guard in ScreeningService (H2 review fix) and the
        // try/catch sentinel path in ScreeningRepository.writeAuditLog() (AC #4).
        TenantContext.setCurrentTenant(tenantA);
        when(dataSourceService.fetchCompanyData(anyString())).thenReturn(buildNullSnapshotCompanyData());

        // When
        SearchResult result = screeningService.search("11112222", userA, tenantA);

        // Then — SearchResult carries the sentinel (not null, not a real hash)
        assertThat(result.sha256Hash()).isEqualTo(ScreeningRepository.HASH_UNAVAILABLE_SENTINEL);
        assertThat(result.cached()).isFalse();

        // And the sentinel is physically written to the DB audit log row
        var auditRecord = dsl.select(SEARCH_AUDIT_LOG.SHA256_HASH, SEARCH_AUDIT_LOG.VERDICT_ID)
                .from(SEARCH_AUDIT_LOG)
                .where(SEARCH_AUDIT_LOG.TENANT_ID.eq(tenantA))
                .and(SEARCH_AUDIT_LOG.TAX_NUMBER.eq("11112222"))
                .fetchOne();
        assertThat(auditRecord).isNotNull();
        assertThat(auditRecord.get(SEARCH_AUDIT_LOG.SHA256_HASH)).isEqualTo(ScreeningRepository.HASH_UNAVAILABLE_SENTINEL);

        // verdict_id FK is still populated even on sentinel path (audit row is complete)
        assertThat(auditRecord.get(SEARCH_AUDIT_LOG.VERDICT_ID)).isNotNull();
        assertThat(auditRecord.get(SEARCH_AUDIT_LOG.VERDICT_ID)).isEqualTo(result.verdictId());
    }

    // --- Fixture helpers ---

    /**
     * Builds CompanyData in the demo adapter single-key format (Story 2.2.2).
     * All risk signals consolidated under a single "demo" adapter key.
     */
    private static CompanyData buildDemoCleanCompanyData() {
        return new CompanyData(
                Map.of("demo", Map.of("available", true, "hasPublicDebt", false,
                        "taxNumberStatus", "ACTIVE", "hasInsolvencyProceedings", false,
                        "companyName", "Példa Kereskedelmi Kft.", "registrationNumber", "01-09-123456",
                        "debtAmount", 0, "debtCurrency", "HUF", "status", "ACTIVE")),
                List.of("demo://in-memory/12345***"),
                Map.of("demo", new ScrapedData("demo",
                        Map.of("available", true, "hasPublicDebt", false, "taxNumberStatus", "ACTIVE",
                                "hasInsolvencyProceedings", false, "companyName", "Példa Kereskedelmi Kft.",
                                "registrationNumber", "01-09-123456", "debtAmount", 0, "debtCurrency", "HUF", "status", "ACTIVE"),
                        List.of("demo://in-memory/12345***"), true, null)),
                "demo-abc123hash");
    }

    /**
     * Builds CompanyData in demo adapter format with public debt.
     */
    private static CompanyData buildDemoDebtCompanyData() {
        return new CompanyData(
                Map.of("demo", Map.of("available", true, "hasPublicDebt", true,
                        "taxNumberStatus", "ACTIVE", "hasInsolvencyProceedings", false,
                        "companyName", "Adós Szolgáltató Bt.", "registrationNumber", "01-09-654321",
                        "debtAmount", 2450000, "debtCurrency", "HUF", "status", "ACTIVE")),
                List.of("demo://in-memory/11223***"),
                Map.of("demo", new ScrapedData("demo",
                        Map.of("available", true, "hasPublicDebt", true, "taxNumberStatus", "ACTIVE",
                                "hasInsolvencyProceedings", false, "companyName", "Adós Szolgáltató Bt.",
                                "registrationNumber", "01-09-654321", "debtAmount", 2450000, "debtCurrency", "HUF", "status", "ACTIVE"),
                        List.of("demo://in-memory/11223***"), true, null)),
                "demo-def456hash");
    }

    /**
     * Legacy 3-adapter format (Stories 2.2 / 2.3 era) — kept for backward compatibility tests.
     */
    private static CompanyData buildCleanCompanyData() {
        return new CompanyData(
                Map.of("nav-debt", Map.of("available", true, "hasPublicDebt", false,
                                "taxNumberStatus", "ACTIVE", "debtAmount", 0, "debtCurrency", "HUF"),
                        "e-cegjegyzek", Map.of("available", true, "companyName", "Test Company",
                                "registrationNumber", "01-09-123456", "status", "ACTIVE"),
                        "cegkozlony", Map.of("available", true, "hasInsolvencyProceedings", false,
                                "hasActiveProceedings", false)),
                List.of("http://nav/test", "http://ceg/test", "http://koz/test"),
                Map.of("nav-debt", new ScrapedData("nav-debt", Map.of("hasPublicDebt", false), List.of("http://nav/test"), true, null),
                        "e-cegjegyzek", new ScrapedData("e-cegjegyzek", Map.of("companyName", "Test Company"), List.of("http://ceg/test"), true, null),
                        "cegkozlony", new ScrapedData("cegkozlony", Map.of("hasInsolvencyProceedings", false), List.of("http://koz/test"), true, null)),
                "abc123hash");
    }

    private static CompanyData buildDebtCompanyData() {
        return new CompanyData(
                Map.of("nav-debt", Map.of("available", true, "hasPublicDebt", true,
                                "taxNumberStatus", "ACTIVE", "debtAmount", 500000, "debtCurrency", "HUF"),
                        "e-cegjegyzek", Map.of("available", true, "companyName", "Debt Company",
                                "registrationNumber", "01-09-654321", "status", "ACTIVE"),
                        "cegkozlony", Map.of("available", true, "hasInsolvencyProceedings", false,
                                "hasActiveProceedings", false)),
                List.of("http://nav/test", "http://ceg/test", "http://koz/test"),
                Map.of("nav-debt", new ScrapedData("nav-debt", Map.of("hasPublicDebt", true), List.of("http://nav/test"), true, null),
                        "e-cegjegyzek", new ScrapedData("e-cegjegyzek", Map.of("companyName", "Debt Company"), List.of("http://ceg/test"), true, null),
                        "cegkozlony", new ScrapedData("cegkozlony", Map.of("hasInsolvencyProceedings", false), List.of("http://koz/test"), true, null)),
                "def456hash");
    }

    private static CompanyData buildSuspendedCompanyData() {
        return new CompanyData(
                Map.of("nav-debt", Map.of("available", true, "hasPublicDebt", false,
                                "taxNumberStatus", "SUSPENDED", "debtAmount", 0, "debtCurrency", "HUF"),
                        "e-cegjegyzek", Map.of("available", true, "companyName", "Suspended Company",
                                "registrationNumber", "01-09-111111", "status", "ACTIVE"),
                        "cegkozlony", Map.of("available", true, "hasInsolvencyProceedings", false,
                                "hasActiveProceedings", false)),
                List.of("http://nav/test", "http://ceg/test", "http://koz/test"),
                Map.of("nav-debt", new ScrapedData("nav-debt", Map.of("taxNumberStatus", "SUSPENDED"), List.of("http://nav/test"), true, null),
                        "e-cegjegyzek", new ScrapedData("e-cegjegyzek", Map.of("companyName", "Suspended Company"), List.of("http://ceg/test"), true, null),
                        "cegkozlony", new ScrapedData("cegkozlony", Map.of("hasInsolvencyProceedings", false), List.of("http://koz/test"), true, null)),
                "jkl012hash");
    }

    private static CompanyData buildPartialFailureCompanyData() {
        return new CompanyData(
                Map.of("nav-debt", Map.of("available", false),
                        "e-cegjegyzek", Map.of("available", true, "companyName", "Partial Company",
                                "registrationNumber", "01-09-789012", "status", "ACTIVE"),
                        "cegkozlony", Map.of("available", true, "hasInsolvencyProceedings", false,
                                "hasActiveProceedings", false)),
                List.of("http://ceg/test", "http://koz/test"),
                Map.of("nav-debt", new ScrapedData("nav-debt", Map.of(), List.of(), false, "Connection timeout"),
                        "e-cegjegyzek", new ScrapedData("e-cegjegyzek", Map.of("companyName", "Partial Company"), List.of("http://ceg/test"), true, null),
                        "cegkozlony", new ScrapedData("cegkozlony", Map.of("hasInsolvencyProceedings", false), List.of("http://koz/test"), true, null)),
                "ghi789hash");
    }

    /**
     * CompanyData with null snapshotData map — simulates a catastrophic adapter failure
     * where no data was collected at all. Used to test the HASH_UNAVAILABLE sentinel path
     * (Story 2.5 AC #4, review fix H2+H3).
     *
     * <p>With null snapshotData: ScreeningService's null-guard sets snapshotJson=null,
     * which causes HashUtil.sha256(null, ...) to throw IAE, caught by writeAuditLog()'s
     * try/catch, writing "HASH_UNAVAILABLE" sentinel to the DB.
     */
    private static CompanyData buildNullSnapshotCompanyData() {
        return new CompanyData(
                null,           // null snapshotData — triggers null-guard in ScreeningService
                List.of(),
                Map.of(),
                null);
    }
}
