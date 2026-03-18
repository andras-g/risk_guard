package hu.riskguard.screening.domain;

import hu.riskguard.core.config.AsyncIngestorHealthState;
import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.security.TenantContext;
import hu.riskguard.datasource.api.dto.CompanyData;
import hu.riskguard.datasource.api.dto.ScrapedData;
import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.notification.domain.NotificationService;
import hu.riskguard.notification.domain.WatchlistPartner;
import hu.riskguard.screening.internal.ScreeningRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AsyncIngestor}.
 * Covers: (a) demo mode run, (b) source unavailable — retention, (c) rate limit delay.
 */
@ExtendWith(MockitoExtension.class)
class AsyncIngestorTest {

    @Mock
    NotificationService notificationService;

    @Mock
    DataSourceService dataSourceService;

    @Mock
    ScreeningRepository screeningRepository;

    AsyncIngestorHealthState healthState;
    RiskGuardProperties properties;
    AsyncIngestor ingestor;

    private static final UUID TENANT_1 = UUID.randomUUID();
    private static final UUID TENANT_2 = UUID.randomUUID();
    private static final UUID SNAPSHOT_1 = UUID.randomUUID();
    private static final UUID SNAPSHOT_2 = UUID.randomUUID();
    private static final String TAX_1 = "12345678";
    private static final String TAX_2 = "99887766";

    @BeforeEach
    void setUp() {
        healthState = new AsyncIngestorHealthState();
        properties = new RiskGuardProperties();
        ingestor = new AsyncIngestor(
                notificationService, dataSourceService, screeningRepository,
                properties, healthState);
    }

    @AfterEach
    void tearDown() {
        // Ensure TenantContext is always clean after tests
        TenantContext.clear();
    }

    @Test
    void ingest_demoMode_allEntriesProcessed_checkedAtUpdated() {
        // Given — demo mode (default), two watchlist entries with existing snapshots
        properties.getDataSource().setMode("demo");

        List<WatchlistPartner> partners = List.of(
                new WatchlistPartner(TENANT_1, TAX_1),
                new WatchlistPartner(TENANT_2, TAX_2));
        when(notificationService.getMonitoredPartners()).thenReturn(partners);

        when(screeningRepository.findLatestSnapshotId(TENANT_1, TAX_1))
                .thenReturn(Optional.of(SNAPSHOT_1));
        when(screeningRepository.findLatestSnapshotId(TENANT_2, TAX_2))
                .thenReturn(Optional.of(SNAPSHOT_2));

        // When
        ingestor.ingest();

        // Then — checked_at updated for both snapshots (demo mode: no data source call)
        verify(screeningRepository).updateSnapshotCheckedAt(eq(SNAPSHOT_1), eq(TENANT_1), any(OffsetDateTime.class));
        verify(screeningRepository).updateSnapshotCheckedAt(eq(SNAPSHOT_2), eq(TENANT_2), any(OffsetDateTime.class));

        // Data source NOT called in demo mode (snapshot data is static)
        verify(dataSourceService, never()).fetchCompanyData(any());

        // Health state recorded
        assertThat(healthState.hasRunAtLeastOnce()).isTrue();
        assertThat(healthState.getLastEntriesProcessed()).isEqualTo(2);
        assertThat(healthState.getLastErrorCount()).isZero();

        // TenantContext cleaned up
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void ingest_sourceUnavailable_existingSnapshotRetained_errorCountIncremented() {
        // Given — live mode, source returns unavailable for one partner
        properties.getDataSource().setMode("live");
        properties.getAsyncIngestor().setDelayBetweenRequestsMs(0); // no delay for test speed

        List<WatchlistPartner> partners = List.of(
                new WatchlistPartner(TENANT_1, TAX_1));
        when(notificationService.getMonitoredPartners()).thenReturn(partners);

        when(screeningRepository.findLatestSnapshotId(TENANT_1, TAX_1))
                .thenReturn(Optional.of(SNAPSHOT_1));

        // Simulate adapter returning SOURCE_UNAVAILABLE
        ScrapedData unavailable = new ScrapedData("nav-debt", Map.of(), List.of(), false, "SOURCE_UNAVAILABLE");
        CompanyData data = new CompanyData(
                Map.of("nav-debt", Map.of("status", "SOURCE_UNAVAILABLE")),
                List.of(), Map.of("nav-debt", unavailable), null);
        when(dataSourceService.fetchCompanyData(TAX_1)).thenReturn(data);

        // When
        ingestor.ingest();

        // Then — existing snapshot NOT overwritten
        verify(screeningRepository, never()).updateSnapshotFromIngestor(any(), any(), any(), any(), any(), any(), any());
        verify(screeningRepository, never()).updateSnapshotCheckedAt(any(), any(), any());

        // Error count incremented
        assertThat(healthState.getLastErrorCount()).isEqualTo(1);
        assertThat(healthState.getLastEntriesProcessed()).isEqualTo(1);

        // TenantContext cleaned up
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void ingest_liveMode_allSourcesAvailable_snapshotUpdated() {
        // Given — live mode, all adapters return successfully
        properties.getDataSource().setMode("live");
        properties.getAsyncIngestor().setDelayBetweenRequestsMs(0);

        List<WatchlistPartner> partners = List.of(
                new WatchlistPartner(TENANT_1, TAX_1));
        when(notificationService.getMonitoredPartners()).thenReturn(partners);

        when(screeningRepository.findLatestSnapshotId(TENANT_1, TAX_1))
                .thenReturn(Optional.of(SNAPSHOT_1));

        // Simulate successful adapter response
        ScrapedData available = new ScrapedData("demo", Map.of("companyName", "Test Kft."),
                List.of("https://example.com"), true, null);
        CompanyData data = new CompanyData(
                Map.of("demo", Map.of("companyName", "Test Kft.")),
                List.of("https://example.com"),
                Map.of("demo", available), "hash123");
        when(dataSourceService.fetchCompanyData(TAX_1)).thenReturn(data);

        // When
        ingestor.ingest();

        // Then — snapshot updated with fresh data including source URLs and fingerprint
        verify(screeningRepository).updateSnapshotFromIngestor(
                eq(SNAPSHOT_1), eq(TENANT_1), eq(data.snapshotData()),
                eq(data.sourceUrls()), eq(data.domFingerprintHash()),
                any(OffsetDateTime.class), eq("live"));

        assertThat(healthState.getLastErrorCount()).isZero();
        assertThat(healthState.getLastEntriesProcessed()).isEqualTo(1);
    }

    @Test
    void ingest_rateLimitDelayApplied_inLiveMode() {
        // Given — live mode with delay configured; use spy to verify sleep was called
        properties.getDataSource().setMode("live");
        properties.getAsyncIngestor().setDelayBetweenRequestsMs(50);

        AsyncIngestor spyIngestor = spy(ingestor);
        doNothing().when(spyIngestor).sleepBetweenRequests(anyLong());

        List<WatchlistPartner> partners = List.of(
                new WatchlistPartner(TENANT_1, TAX_1),
                new WatchlistPartner(TENANT_2, TAX_2));
        when(notificationService.getMonitoredPartners()).thenReturn(partners);

        when(screeningRepository.findLatestSnapshotId(TENANT_1, TAX_1))
                .thenReturn(Optional.of(SNAPSHOT_1));
        when(screeningRepository.findLatestSnapshotId(TENANT_2, TAX_2))
                .thenReturn(Optional.of(SNAPSHOT_2));

        ScrapedData available = new ScrapedData("demo", Map.of("companyName", "Test"),
                List.of(), true, null);
        CompanyData data = new CompanyData(
                Map.of("demo", Map.of("companyName", "Test")),
                List.of(), Map.of("demo", available), "hash");
        when(dataSourceService.fetchCompanyData(any())).thenReturn(data);

        // When
        spyIngestor.ingest();

        // Then — sleep called once per actual data source call (2 entries in live mode)
        verify(spyIngestor, times(2)).sleepBetweenRequests(50L);
        assertThat(healthState.getLastEntriesProcessed()).isEqualTo(2);
    }

    @Test
    void ingest_demoMode_rateLimitDelaySkipped() {
        // Given — demo mode: no data source calls → no delay applied
        properties.getDataSource().setMode("demo");
        properties.getAsyncIngestor().setDelayBetweenRequestsMs(5000);

        AsyncIngestor spyIngestor = spy(ingestor);

        List<WatchlistPartner> partners = List.of(
                new WatchlistPartner(TENANT_1, TAX_1),
                new WatchlistPartner(TENANT_2, TAX_2));
        when(notificationService.getMonitoredPartners()).thenReturn(partners);

        when(screeningRepository.findLatestSnapshotId(TENANT_1, TAX_1))
                .thenReturn(Optional.of(SNAPSHOT_1));
        when(screeningRepository.findLatestSnapshotId(TENANT_2, TAX_2))
                .thenReturn(Optional.of(SNAPSHOT_2));

        // When
        spyIngestor.ingest();

        // Then — sleep never called (demo mode entries don't call data source)
        verify(spyIngestor, never()).sleepBetweenRequests(anyLong());
    }

    @Test
    void ingest_noExistingSnapshot_entrySkipped_noDelay() {
        // Given — partner has no existing snapshot (skip path: no data source call → no delay)
        properties.getDataSource().setMode("live");
        properties.getAsyncIngestor().setDelayBetweenRequestsMs(500);

        AsyncIngestor spyIngestor = spy(ingestor);

        List<WatchlistPartner> partners = List.of(
                new WatchlistPartner(TENANT_1, TAX_1));
        when(notificationService.getMonitoredPartners()).thenReturn(partners);

        when(screeningRepository.findLatestSnapshotId(TENANT_1, TAX_1))
                .thenReturn(Optional.empty());

        // When
        spyIngestor.ingest();

        // Then — no snapshot updates, entry still counted as processed, NO delay applied
        verify(screeningRepository, never()).updateSnapshotCheckedAt(any(), any(), any());
        verify(screeningRepository, never()).updateSnapshotFromIngestor(any(), any(), any(), any(), any(), any(), any());
        verify(spyIngestor, never()).sleepBetweenRequests(anyLong());
        assertThat(healthState.getLastEntriesProcessed()).isEqualTo(1);
        assertThat(healthState.getLastErrorCount()).isZero();
    }

    @Test
    void ingest_emptyWatchlist_noProcessing() {
        // Given — no watchlist entries
        when(notificationService.getMonitoredPartners()).thenReturn(List.of());

        // When
        ingestor.ingest();

        // Then — health state records zero
        assertThat(healthState.hasRunAtLeastOnce()).isTrue();
        assertThat(healthState.getLastEntriesProcessed()).isZero();
        assertThat(healthState.getLastErrorCount()).isZero();
    }

    @Test
    void ingest_exceptionInEntry_doesNotStopOtherEntries() {
        // Given — first entry throws, second succeeds
        properties.getDataSource().setMode("demo");

        List<WatchlistPartner> partners = List.of(
                new WatchlistPartner(TENANT_1, TAX_1),
                new WatchlistPartner(TENANT_2, TAX_2));
        when(notificationService.getMonitoredPartners()).thenReturn(partners);

        when(screeningRepository.findLatestSnapshotId(TENANT_1, TAX_1))
                .thenThrow(new RuntimeException("DB connection failed"));
        when(screeningRepository.findLatestSnapshotId(TENANT_2, TAX_2))
                .thenReturn(Optional.of(SNAPSHOT_2));

        // When
        ingestor.ingest();

        // Then — second entry still processed
        verify(screeningRepository).updateSnapshotCheckedAt(eq(SNAPSHOT_2), eq(TENANT_2), any(OffsetDateTime.class));
        assertThat(healthState.getLastEntriesProcessed()).isEqualTo(2);
        assertThat(healthState.getLastErrorCount()).isEqualTo(1);
    }

    @Test
    void ingest_liveMode_nullSnapshotData_allSourcesAvailable_passesNullToRepository() {
        // Given — live mode, adapter returns available=true but snapshotData is null
        properties.getDataSource().setMode("live");
        properties.getAsyncIngestor().setDelayBetweenRequestsMs(0);

        List<WatchlistPartner> partners = List.of(
                new WatchlistPartner(TENANT_1, TAX_1));
        when(notificationService.getMonitoredPartners()).thenReturn(partners);

        when(screeningRepository.findLatestSnapshotId(TENANT_1, TAX_1))
                .thenReturn(Optional.of(SNAPSHOT_1));

        // Adapter returns available but with null snapshotData
        ScrapedData available = new ScrapedData("demo", Map.of(), List.of(), true, null);
        CompanyData data = new CompanyData(
                null, // null snapshotData
                List.of("https://example.com"),
                Map.of("demo", available), "hash123");
        when(dataSourceService.fetchCompanyData(TAX_1)).thenReturn(data);

        // When
        ingestor.ingest();

        // Then — null snapshotData passed to repository (repository must handle serialization of null)
        verify(screeningRepository).updateSnapshotFromIngestor(
                eq(SNAPSHOT_1), eq(TENANT_1), isNull(),
                eq(List.of("https://example.com")), eq("hash123"),
                any(OffsetDateTime.class), eq("live"));

        assertThat(healthState.getLastEntriesProcessed()).isEqualTo(1);
        assertThat(healthState.getLastErrorCount()).isZero();
    }

    @Test
    void ingest_tenantContextClearedEvenOnFailure() {
        // Given
        properties.getDataSource().setMode("demo");

        List<WatchlistPartner> partners = List.of(
                new WatchlistPartner(TENANT_1, TAX_1));
        when(notificationService.getMonitoredPartners()).thenReturn(partners);

        when(screeningRepository.findLatestSnapshotId(TENANT_1, TAX_1))
                .thenThrow(new RuntimeException("DB failure"));

        // When
        ingestor.ingest();

        // Then — TenantContext must be cleared
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }
}
