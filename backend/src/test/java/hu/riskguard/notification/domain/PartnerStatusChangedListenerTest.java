package hu.riskguard.notification.domain;

import hu.riskguard.notification.internal.NotificationRepository;
import hu.riskguard.notification.internal.NotificationRepository.WatchlistEntryWithUser;
import hu.riskguard.core.events.PartnerStatusChanged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PartnerStatusChangedListener}.
 * Covers: event updates matching watchlist entry, non-watchlisted tax number ignored,
 * multiple tenants with same tax number all updated.
 */
@ExtendWith(MockitoExtension.class)
class PartnerStatusChangedListenerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationService notificationService;

    private PartnerStatusChangedListener listener;

    private static final UUID TENANT_1 = UUID.randomUUID();
    private static final UUID TENANT_2 = UUID.randomUUID();
    private static final UUID USER_1 = UUID.randomUUID();
    private static final UUID USER_2 = UUID.randomUUID();
    private static final UUID VERDICT_ID = UUID.randomUUID();
    private static final String TAX_NUMBER = "12345678";

    @BeforeEach
    void setUp() {
        listener = new PartnerStatusChangedListener(notificationRepository, notificationService);
    }

    @Test
    void onPartnerStatusChanged_updatesMatchingWatchlistEntry() {
        // Given — tax number is on one tenant's watchlist
        PartnerStatusChanged event = PartnerStatusChanged.of(
                VERDICT_ID, TENANT_1, TAX_NUMBER, "RELIABLE", "AT_RISK", null);

        when(notificationRepository.findWatchlistEntriesByTaxNumber(TAX_NUMBER))
                .thenReturn(List.of(new WatchlistPartner(TENANT_1, TAX_NUMBER)));

        // When
        listener.onPartnerStatusChanged(event);

        // Then — verdict status updated for matching entry
        verify(notificationRepository).updateVerdictStatus(
                eq(TENANT_1), eq(TAX_NUMBER), eq("AT_RISK"), any(OffsetDateTime.class));
    }

    @Test
    void onPartnerStatusChanged_nonWatchlistedTaxNumber_silentlyIgnored() {
        // Given — tax number is NOT on any watchlist
        PartnerStatusChanged event = PartnerStatusChanged.of(
                VERDICT_ID, TENANT_1, "99999999", "RELIABLE", "AT_RISK", null);

        when(notificationRepository.findWatchlistEntriesByTaxNumber("99999999"))
                .thenReturn(List.of());

        // When
        listener.onPartnerStatusChanged(event);

        // Then — no updates attempted, no errors
        verify(notificationRepository, never()).updateVerdictStatus(any(), any(), any(), any());
    }

    @Test
    void onPartnerStatusChanged_multipleTenants_allUpdated() {
        // Given — same tax number on two different tenants' watchlists
        PartnerStatusChanged event = PartnerStatusChanged.of(
                VERDICT_ID, TENANT_1, TAX_NUMBER, "INCOMPLETE", "RELIABLE", null);

        when(notificationRepository.findWatchlistEntriesByTaxNumber(TAX_NUMBER))
                .thenReturn(List.of(
                        new WatchlistPartner(TENANT_1, TAX_NUMBER),
                        new WatchlistPartner(TENANT_2, TAX_NUMBER)));

        // When
        listener.onPartnerStatusChanged(event);

        // Then — both tenants' entries updated
        verify(notificationRepository).updateVerdictStatus(
                eq(TENANT_1), eq(TAX_NUMBER), eq("RELIABLE"), any(OffsetDateTime.class));
        verify(notificationRepository).updateVerdictStatus(
                eq(TENANT_2), eq(TAX_NUMBER), eq("RELIABLE"), any(OffsetDateTime.class));
    }

    @Test
    void onPartnerStatusChanged_nullTaxNumber_silentlyIgnored() {
        // Given — event with null tax number (defensive)
        PartnerStatusChanged event = new PartnerStatusChanged(
                VERDICT_ID, TENANT_1, null, "RELIABLE", "AT_RISK", null, OffsetDateTime.now());

        // When
        listener.onPartnerStatusChanged(event);

        // Then — no repository calls
        verify(notificationRepository, never()).findWatchlistEntriesByTaxNumber(any());
        verify(notificationRepository, never()).updateVerdictStatus(any(), any(), any(), any());
    }

    // --- Story 3.8: Outbox record creation tests ---

    @Test
    void onPartnerStatusChanged_statusChange_createsOutboxRecordWithSha256Hash() {
        // Given — genuine status change (RELIABLE → AT_RISK), event carries audit hash (C1 fix)
        PartnerStatusChanged event = PartnerStatusChanged.of(
                VERDICT_ID, TENANT_1, TAX_NUMBER, "RELIABLE", "AT_RISK", "abc123def456");

        when(notificationRepository.findWatchlistEntriesByTaxNumber(TAX_NUMBER))
                .thenReturn(List.of(new WatchlistPartner(TENANT_1, TAX_NUMBER)));
        when(notificationRepository.findWatchlistEntriesWithUserByTaxNumber(TAX_NUMBER))
                .thenReturn(List.of(new WatchlistEntryWithUser(TENANT_1, TAX_NUMBER, "Test Kft", USER_1)));

        // When
        listener.onPartnerStatusChanged(event);

        // Then — outbox record created with SHA-256 hash from event (C1 review fix)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).createAlertNotification(eq(TENANT_1), eq(USER_1), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().get("sha256Hash")).isEqualTo("abc123def456");
    }

    @Test
    void onPartnerStatusChanged_sameStatus_doesNotCreateOutboxRecord() {
        // Given — status didn't actually change (same value)
        PartnerStatusChanged event = PartnerStatusChanged.of(
                VERDICT_ID, TENANT_1, TAX_NUMBER, "RELIABLE", "RELIABLE", null);

        when(notificationRepository.findWatchlistEntriesByTaxNumber(TAX_NUMBER))
                .thenReturn(List.of(new WatchlistPartner(TENANT_1, TAX_NUMBER)));

        // When
        listener.onPartnerStatusChanged(event);

        // Then — watchlist entries updated, but NO outbox record created
        verify(notificationRepository).updateVerdictStatus(any(), any(), any(), any());
        verify(notificationService, never()).createAlertNotification(any(), any(), any());
    }

    @Test
    void onPartnerStatusChanged_nullPreviousStatus_doesNotCreateOutboxRecord() {
        // Given — first-time evaluation (previousStatus is null) — not a "change"
        PartnerStatusChanged event = PartnerStatusChanged.of(
                VERDICT_ID, TENANT_1, TAX_NUMBER, null, "RELIABLE", null);

        when(notificationRepository.findWatchlistEntriesByTaxNumber(TAX_NUMBER))
                .thenReturn(List.of(new WatchlistPartner(TENANT_1, TAX_NUMBER)));

        // When
        listener.onPartnerStatusChanged(event);

        // Then — watchlist entries updated, but NO outbox record (null previousStatus)
        verify(notificationRepository).updateVerdictStatus(any(), any(), any(), any());
        verify(notificationService, never()).createAlertNotification(any(), any(), any());
    }

    @Test
    void onPartnerStatusChanged_multipleTenants_allGetOutboxRecords() {
        // Given — same tax number on two tenants, status changed, event carries hash
        PartnerStatusChanged event = PartnerStatusChanged.of(
                VERDICT_ID, TENANT_1, TAX_NUMBER, "RELIABLE", "AT_RISK", "hash789");

        when(notificationRepository.findWatchlistEntriesByTaxNumber(TAX_NUMBER))
                .thenReturn(List.of(
                        new WatchlistPartner(TENANT_1, TAX_NUMBER),
                        new WatchlistPartner(TENANT_2, TAX_NUMBER)));
        when(notificationRepository.findWatchlistEntriesWithUserByTaxNumber(TAX_NUMBER))
                .thenReturn(List.of(
                        new WatchlistEntryWithUser(TENANT_1, TAX_NUMBER, "Test Kft", USER_1),
                        new WatchlistEntryWithUser(TENANT_2, TAX_NUMBER, "Test Kft", USER_2)));

        // When
        listener.onPartnerStatusChanged(event);

        // Then — outbox records created for both tenants
        verify(notificationService).createAlertNotification(eq(TENANT_1), eq(USER_1), any(Map.class));
        verify(notificationService).createAlertNotification(eq(TENANT_2), eq(USER_2), any(Map.class));
    }
}
