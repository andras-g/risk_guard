package hu.riskguard.notification.domain;

import hu.riskguard.notification.internal.NotificationRepository;
import hu.riskguard.core.events.PartnerStatusChanged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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

    private PartnerStatusChangedListener listener;

    private static final UUID TENANT_1 = UUID.randomUUID();
    private static final UUID TENANT_2 = UUID.randomUUID();
    private static final UUID VERDICT_ID = UUID.randomUUID();
    private static final String TAX_NUMBER = "12345678";

    @BeforeEach
    void setUp() {
        listener = new PartnerStatusChangedListener(notificationRepository);
    }

    @Test
    void onPartnerStatusChanged_updatesMatchingWatchlistEntry() {
        // Given — tax number is on one tenant's watchlist
        PartnerStatusChanged event = PartnerStatusChanged.of(
                VERDICT_ID, TENANT_1, TAX_NUMBER, "RELIABLE", "AT_RISK");

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
                VERDICT_ID, TENANT_1, "99999999", "RELIABLE", "AT_RISK");

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
                VERDICT_ID, TENANT_1, TAX_NUMBER, "INCOMPLETE", "RELIABLE");

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
                VERDICT_ID, TENANT_1, null, "RELIABLE", "AT_RISK", OffsetDateTime.now());

        // When
        listener.onPartnerStatusChanged(event);

        // Then — no repository calls
        verify(notificationRepository, never()).findWatchlistEntriesByTaxNumber(any());
        verify(notificationRepository, never()).updateVerdictStatus(any(), any(), any(), any());
    }
}
