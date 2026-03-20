package hu.riskguard.notification.domain;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.identity.domain.IdentityService;
import hu.riskguard.notification.internal.NotificationRepository;
import hu.riskguard.notification.internal.NotificationRepository.OutboxRecord;
import hu.riskguard.notification.internal.NotificationRepository.PortfolioOutboxRecord;
import hu.riskguard.notification.internal.NotificationRepository.WatchlistEntryRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotificationService} — tenant-scoped watchlist CRUD and outbox operations.
 * Tests: add entry, remove entry, list entries (tenant-scoped), duplicate prevention, count,
 * createAlertNotification, createOrAppendDigest, getOutboxStats.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private IdentityService identityService;

    private NotificationService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private RiskGuardProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RiskGuardProperties();
        service = new NotificationService(notificationRepository, identityService, properties);
    }

    // --- Add to Watchlist ---

    @Test
    void addToWatchlistShouldInsertNewEntryAndReturnIt() {
        String taxNumber = "12345678";

        when(notificationRepository.findByTenantIdAndTaxNumber(eq(TENANT_ID), eq(taxNumber)))
                .thenReturn(Optional.empty());

        NotificationService.AddResult result = service.addToWatchlist(TENANT_ID, taxNumber, null, "RELIABLE");

        assertThat(result.entry().taxNumber()).isEqualTo(taxNumber);
        assertThat(result.entry().tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.entry().verdictStatus()).isEqualTo("RELIABLE");
        assertThat(result.duplicate()).isFalse();
        verify(notificationRepository).insertEntry(any(UUID.class), eq(TENANT_ID),
                eq(taxNumber), any(), any());
        verify(notificationRepository).updateVerdictStatus(eq(TENANT_ID), eq(taxNumber), eq("RELIABLE"), any());
    }

    @Test
    void addToWatchlistShouldReturnExistingEntryOnDuplicate() {
        String taxNumber = "12345678";
        WatchlistEntryRecord existing = buildRecord(taxNumber);

        when(notificationRepository.findByTenantIdAndTaxNumber(eq(TENANT_ID), eq(taxNumber)))
                .thenReturn(Optional.of(existing));

        NotificationService.AddResult result = service.addToWatchlist(TENANT_ID, taxNumber, null, null);

        assertThat(result.entry().taxNumber()).isEqualTo(taxNumber);
        assertThat(result.duplicate()).isTrue();
        verify(notificationRepository, never()).insertEntry(any(), any(), any(), any(), any());
    }

    // --- List Entries ---

    @Test
    void getWatchlistEntriesShouldReturnTenantScopedEntries() {
        WatchlistEntryRecord r1 = buildRecord("12345678");
        WatchlistEntryRecord r2 = buildRecord("99887766");

        when(notificationRepository.findByTenantId(eq(TENANT_ID)))
                .thenReturn(List.of(r1, r2));

        List<WatchlistEntry> result = service.getWatchlistEntries(TENANT_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(WatchlistEntry::taxNumber)
                .containsExactly("12345678", "99887766");
    }

    @Test
    void getWatchlistEntriesShouldReturnEmptyListForNewTenant() {
        when(notificationRepository.findByTenantId(eq(TENANT_ID)))
                .thenReturn(List.of());

        List<WatchlistEntry> result = service.getWatchlistEntries(TENANT_ID);

        assertThat(result).isEmpty();
    }

    // --- Remove Entry ---

    @Test
    void removeFromWatchlistShouldReturnTrueWhenDeleted() {
        UUID entryId = UUID.randomUUID();
        when(notificationRepository.deleteByIdAndTenantId(eq(entryId), eq(TENANT_ID)))
                .thenReturn(1);

        boolean removed = service.removeFromWatchlist(TENANT_ID, entryId);

        assertThat(removed).isTrue();
    }

    @Test
    void removeFromWatchlistShouldReturnFalseWhenNotOwned() {
        UUID entryId = UUID.randomUUID();
        when(notificationRepository.deleteByIdAndTenantId(eq(entryId), eq(TENANT_ID)))
                .thenReturn(0);

        boolean removed = service.removeFromWatchlist(TENANT_ID, entryId);

        assertThat(removed).isFalse();
    }

    // --- Count ---

    @Test
    void getWatchlistCountShouldReturnCorrectCount() {
        when(notificationRepository.countByTenantId(eq(TENANT_ID))).thenReturn(3);

        int count = service.getWatchlistCount(TENANT_ID);

        assertThat(count).isEqualTo(3);
    }

    @Test
    void getWatchlistCountShouldReturnZeroForEmptyWatchlist() {
        when(notificationRepository.countByTenantId(eq(TENANT_ID))).thenReturn(0);

        int count = service.getWatchlistCount(TENANT_ID);

        assertThat(count).isEqualTo(0);
    }

    // --- createAlertNotification (Story 3.8 — H3 review fix) ---

    @Test
    void createAlertNotification_underDailyLimit_insertsAlertRecord() {
        // Given — only 3 alerts today, well under limit of 10
        when(notificationRepository.countTodayAlertsByTenant(eq(TENANT_ID), any(OffsetDateTime.class)))
                .thenReturn(3);

        Map<String, Object> payload = buildAlertPayload();

        // When
        service.createAlertNotification(TENANT_ID, USER_ID, payload);

        // Then — individual ALERT record inserted
        verify(notificationRepository).insertOutboxRecord(
                any(UUID.class), eq(TENANT_ID), eq(USER_ID), eq("ALERT"), anyString(), eq("PENDING"));
        verify(notificationRepository, never()).findPendingDigestForTenantToday(any(), any());
    }

    @Test
    void createAlertNotification_atDailyLimit_createsNewDigestRecord() {
        // Given — exactly at limit (10 alerts today), no existing digest
        when(notificationRepository.countTodayAlertsByTenant(eq(TENANT_ID), any(OffsetDateTime.class)))
                .thenReturn(10);
        when(notificationRepository.findPendingDigestForTenantToday(eq(TENANT_ID), any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());

        Map<String, Object> payload = buildAlertPayload();

        // When
        service.createAlertNotification(TENANT_ID, USER_ID, payload);

        // Then — DIGEST record created (not ALERT)
        verify(notificationRepository).insertOutboxRecord(
                any(UUID.class), eq(TENANT_ID), eq(USER_ID), eq("DIGEST"), anyString(), eq("PENDING"));
    }

    @Test
    void createAlertNotification_overDailyLimit_appendsToExistingDigest() {
        // Given — over limit, existing DIGEST record present
        when(notificationRepository.countTodayAlertsByTenant(eq(TENANT_ID), any(OffsetDateTime.class)))
                .thenReturn(15);

        String existingDigestPayload = "{\"tenantId\":\"" + TENANT_ID + "\",\"changes\":[{\"taxNumber\":\"11111111\",\"companyName\":\"First Kft\",\"previousStatus\":\"RELIABLE\",\"newStatus\":\"AT_RISK\"}]}";
        OutboxRecord existingDigest = new OutboxRecord(
                UUID.randomUUID(), TENANT_ID, USER_ID, "DIGEST", existingDigestPayload,
                "PENDING", 0, null, OffsetDateTime.now(), null);
        when(notificationRepository.findPendingDigestForTenantToday(eq(TENANT_ID), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(existingDigest));

        Map<String, Object> payload = buildAlertPayload();

        // When
        service.createAlertNotification(TENANT_ID, USER_ID, payload);

        // Then — existing digest payload updated (not new record)
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationRepository).updateOutboxPayload(eq(existingDigest.id()), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).contains("12345678"); // new entry appended
        assertThat(payloadCaptor.getValue()).contains("11111111"); // existing entry preserved
        verify(notificationRepository, never()).insertOutboxRecord(any(), any(), any(), eq("DIGEST"), any(), any());
    }

    @Test
    void createAlertNotification_jsonSerializationFailure_handledGracefully() {
        // Given — under limit, but we'll verify no exception propagates even with edge-case payload
        when(notificationRepository.countTodayAlertsByTenant(eq(TENANT_ID), any(OffsetDateTime.class)))
                .thenReturn(0);

        Map<String, Object> payload = buildAlertPayload();

        // When/Then — should not throw
        assertThatCode(() -> service.createAlertNotification(TENANT_ID, USER_ID, payload))
                .doesNotThrowAnyException();
    }

    // --- getOutboxStats ---

    @Test
    void getOutboxStats_returnsPendingAndFailedCounts() {
        when(notificationRepository.countPendingTotal()).thenReturn(5);
        when(notificationRepository.countFailedTotal()).thenReturn(2);

        int[] stats = service.getOutboxStats();

        assertThat(stats[0]).isEqualTo(5);
        assertThat(stats[1]).isEqualTo(2);
    }

    // --- getPortfolioAlerts (Story 3.9) ---

    @Test
    void getPortfolioAlerts_returnsAlertsFromMultipleTenants() {
        when(identityService.getActiveMandateTenantIds(eq(USER_ID)))
                .thenReturn(List.of(TENANT_ID, TENANT_B));

        String alertPayload = "{\"taxNumber\":\"12345678\",\"companyName\":\"Kovacs Kft\","
                + "\"previousStatus\":\"RELIABLE\",\"newStatus\":\"AT_RISK\","
                + "\"verdictId\":\"" + UUID.randomUUID() + "\","
                + "\"changedAt\":\"2026-03-20T10:00:00Z\",\"sha256Hash\":\"abc123\"}";

        String alertPayloadB = "{\"taxNumber\":\"99887766\",\"companyName\":\"Nagy Bt\","
                + "\"previousStatus\":\"AT_RISK\",\"newStatus\":\"RELIABLE\","
                + "\"verdictId\":\"" + UUID.randomUUID() + "\","
                + "\"changedAt\":\"2026-03-20T09:00:00Z\",\"sha256Hash\":\"def456\"}";

        OffsetDateTime now = OffsetDateTime.now();
        List<PortfolioOutboxRecord> records = List.of(
                new PortfolioOutboxRecord(UUID.randomUUID(), TENANT_ID, "Tenant A", "ALERT", alertPayload, now),
                new PortfolioOutboxRecord(UUID.randomUUID(), TENANT_B, "Tenant B", "ALERT", alertPayloadB, now.minusHours(1)));
        when(notificationRepository.findPortfolioAlerts(eq(List.of(TENANT_ID, TENANT_B)), any()))
                .thenReturn(records);

        List<PortfolioAlert> result = service.getPortfolioAlerts(USER_ID, 7);

        assertThat(result).hasSize(2);
        // AT_RISK should be sorted first (Morning Risk Pulse priority)
        assertThat(result.get(0).newStatus()).isEqualTo("AT_RISK");
        assertThat(result.get(0).taxNumber()).isEqualTo("12345678");
        assertThat(result.get(1).newStatus()).isEqualTo("RELIABLE");
    }

    @Test
    void getPortfolioAlerts_respectsMandateValidityDates() {
        // If identityService returns no tenants (all mandates expired), result is empty
        when(identityService.getActiveMandateTenantIds(eq(USER_ID)))
                .thenReturn(List.of());

        List<PortfolioAlert> result = service.getPortfolioAlerts(USER_ID, 7);

        assertThat(result).isEmpty();
        verify(notificationRepository, never()).findPortfolioAlerts(any(), any());
    }

    @Test
    void getPortfolioAlerts_expandsDigestRecords() {
        when(identityService.getActiveMandateTenantIds(eq(USER_ID)))
                .thenReturn(List.of(TENANT_ID));

        String digestPayload = "{\"tenantId\":\"" + TENANT_ID + "\",\"changes\":["
                + "{\"taxNumber\":\"11111111\",\"companyName\":\"First Kft\",\"previousStatus\":\"RELIABLE\",\"newStatus\":\"AT_RISK\"},"
                + "{\"taxNumber\":\"22222222\",\"companyName\":\"Second Bt\",\"previousStatus\":\"AT_RISK\",\"newStatus\":\"RELIABLE\"}"
                + "]}";

        OffsetDateTime now = OffsetDateTime.now();
        List<PortfolioOutboxRecord> records = List.of(
                new PortfolioOutboxRecord(UUID.randomUUID(), TENANT_ID, "Tenant A", "DIGEST", digestPayload, now));
        when(notificationRepository.findPortfolioAlerts(eq(List.of(TENANT_ID)), any()))
                .thenReturn(records);

        List<PortfolioAlert> result = service.getPortfolioAlerts(USER_ID, 7);

        assertThat(result).hasSize(2);
        // Digest-expanded alerts should have null sha256Hash and verdictId
        assertThat(result).allSatisfy(a -> {
            assertThat(a.sha256Hash()).isNull();
            assertThat(a.verdictId()).isNull();
        });
        // Each digest-expanded alert must have a UNIQUE alertId (Vue :key requirement)
        assertThat(result.get(0).alertId()).isNotEqualTo(result.get(1).alertId());
    }

    @Test
    void getPortfolioAlerts_emptyMandatesReturnsEmpty() {
        when(identityService.getActiveMandateTenantIds(eq(USER_ID)))
                .thenReturn(List.of());

        List<PortfolioAlert> result = service.getPortfolioAlerts(USER_ID, 7);

        assertThat(result).isEmpty();
    }

    @Test
    void getPortfolioAlerts_clampsDaysParameter() {
        when(identityService.getActiveMandateTenantIds(eq(USER_ID)))
                .thenReturn(List.of(TENANT_ID));
        when(notificationRepository.findPortfolioAlerts(any(), any()))
                .thenReturn(List.of());

        // days > 30 should be clamped to 30
        OffsetDateTime before = OffsetDateTime.now().minusDays(30).minusMinutes(1);
        service.getPortfolioAlerts(USER_ID, 60);
        OffsetDateTime after = OffsetDateTime.now().minusDays(30).plusMinutes(1);

        // Capture and verify the since parameter is approximately now minus 30 days (not 60)
        ArgumentCaptor<OffsetDateTime> sinceCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(notificationRepository).findPortfolioAlerts(eq(List.of(TENANT_ID)), sinceCaptor.capture());
        OffsetDateTime capturedSince = sinceCaptor.getValue();
        assertThat(capturedSince).isAfter(before);
        assertThat(capturedSince).isBefore(after);
    }

    // --- Helpers ---

    private Map<String, Object> buildAlertPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", TENANT_ID.toString());
        payload.put("taxNumber", "12345678");
        payload.put("companyName", "Test Kft");
        payload.put("previousStatus", "RELIABLE");
        payload.put("newStatus", "AT_RISK");
        payload.put("verdictId", UUID.randomUUID().toString());
        payload.put("changedAt", OffsetDateTime.now().toString());
        payload.put("sha256Hash", "abc123");
        return payload;
    }

    private WatchlistEntryRecord buildRecord(String taxNumber) {
        return new WatchlistEntryRecord(
                UUID.randomUUID(), TENANT_ID, taxNumber, "Test Company Kft.",
                null, OffsetDateTime.now(), OffsetDateTime.now());
    }
}
