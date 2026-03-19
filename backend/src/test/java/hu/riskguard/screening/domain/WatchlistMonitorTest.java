package hu.riskguard.screening.domain;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.config.WatchlistMonitorHealthState;
import hu.riskguard.core.security.TenantContext;
import hu.riskguard.notification.domain.MonitoredPartner;
import hu.riskguard.notification.domain.NotificationService;
import hu.riskguard.screening.domain.ScreeningService.SnapshotVerdictResult;
import hu.riskguard.core.events.PartnerStatusChanged;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WatchlistMonitor}.
 * Covers: (a) status change detection (RELIABLE→AT_RISK), (b) no change (same status),
 * (c) transient failure (INCOMPLETE not overwriting), (d) demo mode run, (e) rate limit delay,
 * (f) empty watchlist, (g) exception isolation per entry.
 */
@ExtendWith(MockitoExtension.class)
class WatchlistMonitorTest {

    @Mock NotificationService notificationService;
    @Mock ScreeningService screeningService;
    @Mock ApplicationEventPublisher eventPublisher;

    WatchlistMonitorHealthState healthState;
    RiskGuardProperties properties;
    WatchlistMonitor monitor;

    private static final UUID TENANT_1 = UUID.randomUUID();
    private static final UUID TENANT_2 = UUID.randomUUID();
    private static final UUID VERDICT_1 = UUID.randomUUID();
    private static final String TAX_1 = "12345678";
    private static final String TAX_2 = "99887766";

    @BeforeEach
    void setUp() {
        healthState = new WatchlistMonitorHealthState();
        properties = new RiskGuardProperties();
        properties.getDataSource().setMode("demo");
        properties.getWatchlistMonitor().setDelayBetweenEvaluationsMs(0);
        monitor = new WatchlistMonitor(
                notificationService, screeningService,
                eventPublisher, properties, healthState);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void monitor_statusChange_publishesEventAndUpdatesEntry() {
        // Given — partner's last verdict was RELIABLE, new verdict is AT_RISK
        var partner = new MonitoredPartner(TENANT_1, TAX_1, "RELIABLE");
        when(notificationService.getMonitoredPartnersWithVerdicts()).thenReturn(List.of(partner));

        var current = new SnapshotVerdictResult(VERDICT_1, "AT_RISK", OffsetDateTime.now(), false);
        when(screeningService.getLatestSnapshotWithVerdict(TENANT_1, TAX_1)).thenReturn(current);

        // When
        monitor.monitor();

        // Then — event published with status change
        ArgumentCaptor<PartnerStatusChanged> captor = ArgumentCaptor.forClass(PartnerStatusChanged.class);
        verify(eventPublisher).publishEvent(captor.capture());
        PartnerStatusChanged event = captor.getValue();
        assertThat(event.previousStatus()).isEqualTo("RELIABLE");
        assertThat(event.newStatus()).isEqualTo("AT_RISK");
        assertThat(event.tenantId()).isEqualTo(TENANT_1);
        assertThat(event.taxNumber()).isEqualTo(TAX_1);

        // Verdict status NOT updated directly by monitor — the PartnerStatusChangedListener
        // handles it reactively via the published event (prevents double-write).
        verify(notificationService, never()).updateVerdictStatus(any(), any(), any(), any());

        // Health state recorded
        assertThat(healthState.getLastChangesDetected()).isEqualTo(1);
        assertThat(healthState.getLastEntriesProcessed()).isEqualTo(1);
        assertThat(healthState.getLastErrorCount()).isZero();

        // TenantContext cleaned up
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void monitor_noChange_sameStatus_noEventPublished() {
        // Given — partner's last verdict is RELIABLE, new verdict is also RELIABLE
        var partner = new MonitoredPartner(TENANT_1, TAX_1, "RELIABLE");
        when(notificationService.getMonitoredPartnersWithVerdicts()).thenReturn(List.of(partner));

        var current = new SnapshotVerdictResult(VERDICT_1, "RELIABLE", OffsetDateTime.now(), false);
        when(screeningService.getLatestSnapshotWithVerdict(TENANT_1, TAX_1)).thenReturn(current);

        // When
        monitor.monitor();

        // Then — no event published
        verify(eventPublisher, never()).publishEvent(any());

        // But verdict status still updated (refreshes timestamp)
        verify(notificationService).updateVerdictStatus(
                eq(TENANT_1), eq(TAX_1), eq("RELIABLE"), any(OffsetDateTime.class));

        assertThat(healthState.getLastChangesDetected()).isZero();
        assertThat(healthState.getLastEntriesProcessed()).isEqualTo(1);
    }

    @Test
    void monitor_transientFailure_existingStatusNotOverwritten() {
        // Given — partner has RELIABLE status, but current verdict is transient failure
        var partner = new MonitoredPartner(TENANT_1, TAX_1, "RELIABLE");
        when(notificationService.getMonitoredPartnersWithVerdicts()).thenReturn(List.of(partner));

        var current = new SnapshotVerdictResult(VERDICT_1, "INCOMPLETE", OffsetDateTime.now(), true);
        when(screeningService.getLatestSnapshotWithVerdict(TENANT_1, TAX_1)).thenReturn(current);

        // When
        monitor.monitor();

        // Then — existing RELIABLE status NOT overwritten with INCOMPLETE
        verify(notificationService, never()).updateVerdictStatus(any(), any(), any(), any());

        // But last_checked_at IS updated to record monitoring was attempted
        verify(notificationService).updateCheckedAt(eq(TENANT_1), eq(TAX_1), any(OffsetDateTime.class));

        // No event published for transient failure
        verify(eventPublisher, never()).publishEvent(any());

        // Error counted
        assertThat(healthState.getLastErrorCount()).isEqualTo(1);
        assertThat(healthState.getLastEntriesProcessed()).isEqualTo(1);
    }

    @Test
    void monitor_demoMode_noChangesDetected_infrastructureValidated() {
        // Given — demo mode, static data means same verdict
        properties.getDataSource().setMode("demo");
        var partner = new MonitoredPartner(TENANT_1, TAX_1, "RELIABLE");
        when(notificationService.getMonitoredPartnersWithVerdicts()).thenReturn(List.of(partner));

        var current = new SnapshotVerdictResult(VERDICT_1, "RELIABLE", OffsetDateTime.now(), false);
        when(screeningService.getLatestSnapshotWithVerdict(TENANT_1, TAX_1)).thenReturn(current);

        // When
        monitor.monitor();

        // Then — infrastructure validated: entry processed, no changes
        verify(notificationService).updateVerdictStatus(
                eq(TENANT_1), eq(TAX_1), eq("RELIABLE"), any(OffsetDateTime.class));
        verify(eventPublisher, never()).publishEvent(any());

        assertThat(healthState.getLastChangesDetected()).isZero();
        assertThat(healthState.getLastEntriesProcessed()).isEqualTo(1);
    }

    @Test
    void monitor_rateLimitDelay_appliedInLiveMode() {
        // Given — live mode with delay configured
        properties.getDataSource().setMode("live");
        properties.getWatchlistMonitor().setDelayBetweenEvaluationsMs(100);

        WatchlistMonitor spyMonitor = spy(monitor);
        doNothing().when(spyMonitor).sleepBetweenEvaluations(anyLong());

        var partners = List.of(
                new MonitoredPartner(TENANT_1, TAX_1, "RELIABLE"),
                new MonitoredPartner(TENANT_2, TAX_2, "RELIABLE"));
        when(notificationService.getMonitoredPartnersWithVerdicts()).thenReturn(partners);

        when(screeningService.getLatestSnapshotWithVerdict(eq(TENANT_1), eq(TAX_1)))
                .thenReturn(new SnapshotVerdictResult(VERDICT_1, "RELIABLE", OffsetDateTime.now(), false));
        when(screeningService.getLatestSnapshotWithVerdict(eq(TENANT_2), eq(TAX_2)))
                .thenReturn(new SnapshotVerdictResult(UUID.randomUUID(), "RELIABLE", OffsetDateTime.now(), false));

        // When
        spyMonitor.monitor();

        // Then — sleep called for each entry in live mode
        verify(spyMonitor, times(2)).sleepBetweenEvaluations(100L);
    }

    @Test
    void monitor_demoMode_rateLimitDelaySkipped() {
        // Given — demo mode with delay configured (should be skipped)
        properties.getDataSource().setMode("demo");
        properties.getWatchlistMonitor().setDelayBetweenEvaluationsMs(5000);

        WatchlistMonitor spyMonitor = spy(monitor);

        var partner = new MonitoredPartner(TENANT_1, TAX_1, "RELIABLE");
        when(notificationService.getMonitoredPartnersWithVerdicts()).thenReturn(List.of(partner));

        when(screeningService.getLatestSnapshotWithVerdict(TENANT_1, TAX_1))
                .thenReturn(new SnapshotVerdictResult(VERDICT_1, "RELIABLE", OffsetDateTime.now(), false));

        // When
        spyMonitor.monitor();

        // Then — sleep never called in demo mode
        verify(spyMonitor, never()).sleepBetweenEvaluations(anyLong());
    }

    @Test
    void monitor_emptyWatchlist_noProcessing() {
        // Given — no watchlist entries
        when(notificationService.getMonitoredPartnersWithVerdicts()).thenReturn(List.of());

        // When
        monitor.monitor();

        // Then — health state recorded zero
        assertThat(healthState.hasRunAtLeastOnce()).isTrue();
        assertThat(healthState.getLastEntriesProcessed()).isZero();
        assertThat(healthState.getLastChangesDetected()).isZero();
        assertThat(healthState.getLastErrorCount()).isZero();
    }

    @Test
    void monitor_exceptionInEntry_doesNotStopOtherEntries() {
        // Given — first entry throws, second succeeds
        var partners = List.of(
                new MonitoredPartner(TENANT_1, TAX_1, "RELIABLE"),
                new MonitoredPartner(TENANT_2, TAX_2, "AT_RISK"));
        when(notificationService.getMonitoredPartnersWithVerdicts()).thenReturn(partners);

        when(screeningService.getLatestSnapshotWithVerdict(TENANT_1, TAX_1))
                .thenThrow(new RuntimeException("DB connection failed"));
        when(screeningService.getLatestSnapshotWithVerdict(TENANT_2, TAX_2))
                .thenReturn(new SnapshotVerdictResult(UUID.randomUUID(), "AT_RISK", OffsetDateTime.now(), false));

        // When
        monitor.monitor();

        // Then — second entry still processed
        verify(notificationService).updateVerdictStatus(
                eq(TENANT_2), eq(TAX_2), eq("AT_RISK"), any(OffsetDateTime.class));
        assertThat(healthState.getLastEntriesProcessed()).isEqualTo(1);
        assertThat(healthState.getLastErrorCount()).isEqualTo(1);

        // TenantContext cleaned up
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void monitor_noVerdictExists_updatesCheckedAtOnly() {
        // Given — partner exists on watchlist but no verdict in screening module
        var partner = new MonitoredPartner(TENANT_1, TAX_1, null);
        when(notificationService.getMonitoredPartnersWithVerdicts()).thenReturn(List.of(partner));

        when(screeningService.getLatestSnapshotWithVerdict(TENANT_1, TAX_1)).thenReturn(null);

        // When
        monitor.monitor();

        // Then — only checked_at updated (no verdict to compare)
        verify(notificationService).updateCheckedAt(eq(TENANT_1), eq(TAX_1), any(OffsetDateTime.class));
        verify(notificationService, never()).updateVerdictStatus(any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());

        assertThat(healthState.getLastEntriesProcessed()).isEqualTo(1);
        assertThat(healthState.getLastChangesDetected()).isZero();
    }

    @Test
    void monitor_tenantContextClearedEvenOnFailure() {
        // Given
        var partner = new MonitoredPartner(TENANT_1, TAX_1, "RELIABLE");
        when(notificationService.getMonitoredPartnersWithVerdicts()).thenReturn(List.of(partner));
        when(screeningService.getLatestSnapshotWithVerdict(TENANT_1, TAX_1))
                .thenThrow(new RuntimeException("DB failure"));

        // When
        monitor.monitor();

        // Then — TenantContext must be cleared
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }
}
