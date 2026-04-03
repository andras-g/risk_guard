package hu.riskguard.notification;

import hu.riskguard.notification.domain.WatchlistPartner;
import hu.riskguard.notification.internal.NotificationRepository;
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
import java.util.List;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.TENANTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Integration test for {@link NotificationRepository} — validates that
 * {@code findAllWatchlistEntries()} correctly maps database rows to
 * {@link WatchlistPartner} records using jOOQ's {@code fetchInto()}.
 *
 * <p>This test exists because the repository uses raw jOOQ DSL references
 * (not generated type-safe code), so a mapping failure would be silent at
 * compile time and only visible at runtime. See review finding [HIGH]:
 * "fetchInto(WatchlistPartner.class) uses raw jOOQ DSL with no integration test".
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class NotificationRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DSLContext dsl;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void setUp() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();

        // Ensure watchlist table is clean before each test (seed data may be present)
        dsl.deleteFrom(table("watchlist_entries")).execute();

        // Insert test tenants (required FK for watchlist_entries.tenant_id)
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantA)
                .set(TENANTS.NAME, "Tenant A Kft.")
                .set(TENANTS.TIER, "ALAP")
                .set(TENANTS.CREATED_AT, now)
                .execute();
        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantB)
                .set(TENANTS.NAME, "Tenant B Kft.")
                .set(TENANTS.TIER, "ALAP")
                .set(TENANTS.CREATED_AT, now)
                .execute();
    }

    @AfterEach
    void tearDown() {
        dsl.deleteFrom(table("watchlist_entries")).execute();
        dsl.deleteFrom(TENANTS).where(TENANTS.ID.in(tenantA, tenantB)).execute();
    }

    @Test
    void findAllWatchlistEntries_returnsAllEntriesAcrossTenants() {
        // Given — two entries across different tenants
        insertWatchlistEntry(tenantA, "12345678");
        insertWatchlistEntry(tenantB, "99887766");

        // When
        List<WatchlistPartner> partners = notificationRepository.findAllWatchlistEntries();

        // Then — both entries returned with correct mapping
        assertThat(partners).hasSize(2);
        assertThat(partners).extracting(WatchlistPartner::taxNumber)
                .containsExactlyInAnyOrder("12345678", "99887766");
        assertThat(partners).extracting(WatchlistPartner::tenantId)
                .containsExactlyInAnyOrder(tenantA, tenantB);
    }

    @Test
    void findAllWatchlistEntries_emptyTable_returnsEmptyList() {
        // Given — no entries

        // When
        List<WatchlistPartner> partners = notificationRepository.findAllWatchlistEntries();

        // Then
        assertThat(partners).isEmpty();
    }

    @Test
    void findAllWatchlistEntries_multipleSameTenant_returnsAll() {
        // Given — multiple entries for the same tenant
        insertWatchlistEntry(tenantA, "12345678");
        insertWatchlistEntry(tenantA, "11223344");

        // When
        List<WatchlistPartner> partners = notificationRepository.findAllWatchlistEntries();

        // Then
        assertThat(partners).hasSize(2);
        assertThat(partners).allMatch(p -> p.tenantId().equals(tenantA));
    }

    @Test
    void updateVerdictStatusWithHash_capturesPreviousVerdictStatus() {
        // Given — entry with an existing verdict status
        insertWatchlistEntryWithVerdictStatus(tenantA, "12345678", "RELIABLE");

        // When — verdict changes to AT_RISK
        notificationRepository.updateVerdictStatusWithHash(
                tenantA, "12345678", "AT_RISK", OffsetDateTime.now(), null);

        // Then — previous_verdict_status holds the OLD value, current holds the NEW value
        var entry = notificationRepository.findByTenantIdAndTaxNumber(tenantA, "12345678");
        assertThat(entry).isPresent();
        assertThat(entry.get().verdictStatus()).isEqualTo("AT_RISK");
        assertThat(entry.get().previousVerdictStatus()).isEqualTo("RELIABLE");
    }

    @Test
    void updateVerdictStatusWithHash_doesNotOverwriteHashWhenNullProvided() {
        // Given — entry with an existing hash
        String existingHash = "b".repeat(64);
        insertWatchlistEntryWithHash(tenantA, "12345678", existingHash);

        // When — update with null sha256Hash (should NOT overwrite the existing hash)
        notificationRepository.updateVerdictStatusWithHash(
                tenantA, "12345678", "AT_RISK", OffsetDateTime.now(), null);

        // Then — existing hash is preserved
        var entry = notificationRepository.findByTenantIdAndTaxNumber(tenantA, "12345678");
        assertThat(entry).isPresent();
        assertThat(entry.get().latestSha256Hash()).isEqualTo(existingHash);
    }

    @Test
    void updateVerdictStatusWithHash_doesNotOverwriteHashWhenSentinelProvided() {
        // Given — entry with an existing hash
        String existingHash = "c".repeat(64);
        insertWatchlistEntryWithHash(tenantA, "12345678", existingHash);

        // When — update with sentinel value (should NOT overwrite)
        notificationRepository.updateVerdictStatusWithHash(
                tenantA, "12345678", "RELIABLE", OffsetDateTime.now(), "HASH_UNAVAILABLE");

        // Then — existing hash is preserved
        var entry = notificationRepository.findByTenantIdAndTaxNumber(tenantA, "12345678");
        assertThat(entry).isPresent();
        assertThat(entry.get().latestSha256Hash()).isEqualTo(existingHash);
    }

    @Test
    void updateVerdictStatusWithHash_updatesHashWhenValidHashProvided() {
        // Given — entry without a hash
        insertWatchlistEntry(tenantA, "12345678");
        String newHash = "d".repeat(64);

        // When — update with a valid hash
        notificationRepository.updateVerdictStatusWithHash(
                tenantA, "12345678", "RELIABLE", OffsetDateTime.now(), newHash);

        // Then — hash is written
        var entry = notificationRepository.findByTenantIdAndTaxNumber(tenantA, "12345678");
        assertThat(entry).isPresent();
        assertThat(entry.get().latestSha256Hash()).isEqualTo(newHash);
    }

    private void insertWatchlistEntry(UUID tenantId, String taxNumber) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("watchlist_entries"))
                .set(field("id", UUID.class), UUID.randomUUID())
                .set(field("tenant_id", UUID.class), tenantId)
                .set(field("tax_number", String.class), taxNumber)
                .set(field("created_at", OffsetDateTime.class), now)
                .set(field("updated_at", OffsetDateTime.class), now)
                .execute();
    }

    private void insertWatchlistEntryWithVerdictStatus(UUID tenantId, String taxNumber, String verdictStatus) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("watchlist_entries"))
                .set(field("id", UUID.class), UUID.randomUUID())
                .set(field("tenant_id", UUID.class), tenantId)
                .set(field("tax_number", String.class), taxNumber)
                .set(field("last_verdict_status", String.class), verdictStatus)
                .set(field("created_at", OffsetDateTime.class), now)
                .set(field("updated_at", OffsetDateTime.class), now)
                .execute();
    }

    private void insertWatchlistEntryWithHash(UUID tenantId, String taxNumber, String sha256Hash) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("watchlist_entries"))
                .set(field("id", UUID.class), UUID.randomUUID())
                .set(field("tenant_id", UUID.class), tenantId)
                .set(field("tax_number", String.class), taxNumber)
                .set(field("company_name", String.class), "Test Company Kft.")
                .set(field("latest_sha256_hash", String.class), sha256Hash)
                .set(field("created_at", OffsetDateTime.class), now)
                .set(field("updated_at", OffsetDateTime.class), now)
                .execute();
    }
}
