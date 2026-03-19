package hu.riskguard.notification.domain;

import hu.riskguard.notification.internal.NotificationRepository;
import hu.riskguard.notification.internal.NotificationRepository.WatchlistEntryRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotificationService} — tenant-scoped watchlist CRUD.
 * Tests: add entry, remove entry, list entries (tenant-scoped), duplicate prevention, count.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService service;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository);
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

    // --- Helpers ---

    private WatchlistEntryRecord buildRecord(String taxNumber) {
        return new WatchlistEntryRecord(
                UUID.randomUUID(), TENANT_ID, taxNumber, "Test Company Kft.",
                null, OffsetDateTime.now(), OffsetDateTime.now());
    }
}
