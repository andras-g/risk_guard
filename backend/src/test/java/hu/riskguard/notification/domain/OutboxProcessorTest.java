package hu.riskguard.notification.domain;

// ObjectMapper is created internally by OutboxProcessor
import hu.riskguard.core.config.OutboxHealthState;
import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.identity.domain.IdentityService;
import hu.riskguard.notification.internal.EmailTemplateRenderer;
import hu.riskguard.notification.internal.NotificationRepository;
import hu.riskguard.notification.internal.NotificationRepository.OutboxRecord;
import hu.riskguard.notification.internal.ResendEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OutboxProcessor}.
 * Covers: happy path, failure with retry, max retries, demo mode, missing API key,
 * batch limit, empty queue.
 */
@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private IdentityService identityService;
    @Mock private EmailTemplateRenderer templateRenderer;
    @Mock private ResendEmailSender resendEmailSender;
    @Mock private OutboxHealthState healthState;

    private RiskGuardProperties properties;
    private OutboxProcessor processor;

    private static final UUID RECORD_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new RiskGuardProperties();
        properties.getEmail().setEnabled(true);
        properties.getEmail().setMaxRetriesCount(5);
        properties.getEmail().setBaseBackoffSeconds(30);
        properties.getEmail().setMaxBackoffSeconds(3600);
        properties.getOutbox().setBatchSize(50);

        processor = new OutboxProcessor(
                notificationRepository, identityService, templateRenderer,
                resendEmailSender, properties, healthState);
    }

    private OutboxRecord createAlertRecord(int retryCount) {
        String payload = "{\"companyName\":\"Test Kft\",\"taxNumber\":\"12345678\","
                + "\"previousStatus\":\"RELIABLE\",\"newStatus\":\"AT_RISK\","
                + "\"changedAt\":\"2026-03-20T10:00:00Z\",\"sha256Hash\":\"abc123\"}";
        return new OutboxRecord(
                RECORD_ID, TENANT_ID, USER_ID, "ALERT", payload, "PENDING",
                retryCount, null, OffsetDateTime.now(), null);
    }

    @Test
    void processOutbox_happyPath_pendingToSent() {
        // Given
        OutboxRecord record = createAlertRecord(0);
        when(notificationRepository.findPendingOutboxRecords(50)).thenReturn(List.of(record));
        when(identityService.getUserEmail(USER_ID)).thenReturn("user@example.com");
        when(identityService.getUserPreferredLanguage(USER_ID)).thenReturn("hu");
        when(templateRenderer.renderAlertSubject(eq("Test Kft"), any(Locale.class)))
                .thenReturn("Partner állapotváltozás: Test Kft");
        when(templateRenderer.renderAlertBody(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("<html>body</html>");
        when(resendEmailSender.send(eq("user@example.com"), anyString(), anyString()))
                .thenReturn(true);
        when(notificationRepository.countPendingTotal()).thenReturn(0);
        when(notificationRepository.countFailedTotal()).thenReturn(0);

        // When
        processor.processOutbox();

        // Then
        verify(notificationRepository).updateOutboxSent(RECORD_ID);
        verify(healthState).recordRun(1, 0, 0);
    }

    @Test
    void processOutbox_failureWithRetry_backoffCalculated() {
        // Given
        OutboxRecord record = createAlertRecord(0);
        when(notificationRepository.findPendingOutboxRecords(50)).thenReturn(List.of(record));
        when(identityService.getUserEmail(USER_ID)).thenReturn("user@example.com");
        when(identityService.getUserPreferredLanguage(USER_ID)).thenReturn("en");
        when(templateRenderer.renderAlertSubject(any(), any())).thenReturn("Subject");
        when(templateRenderer.renderAlertBody(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("<html>body</html>");
        when(resendEmailSender.send(anyString(), anyString(), anyString())).thenReturn(false);
        when(notificationRepository.countPendingTotal()).thenReturn(1);
        when(notificationRepository.countFailedTotal()).thenReturn(0);

        // When
        processor.processOutbox();

        // Then — retry with backoff
        ArgumentCaptor<Integer> retryCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<OffsetDateTime> nextRetryCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(notificationRepository).updateOutboxRetry(eq(RECORD_ID), retryCaptor.capture(), nextRetryCaptor.capture());
        assertThat(retryCaptor.getValue()).isEqualTo(1);
        // Backoff: 2^0 * 30 = 30 seconds (AC5: first retry = 30s)
        assertThat(nextRetryCaptor.getValue()).isAfter(OffsetDateTime.now().plusSeconds(20));
        assertThat(nextRetryCaptor.getValue()).isBefore(OffsetDateTime.now().plusSeconds(40));
        verify(notificationRepository, never()).updateOutboxSent(any());
    }

    @Test
    void processOutbox_maxRetriesExceeded_pendingToFailed() {
        // Given — already at retry count 4, next retry will be count 5 (>= maxRetries)
        OutboxRecord record = createAlertRecord(4);
        when(notificationRepository.findPendingOutboxRecords(50)).thenReturn(List.of(record));
        when(identityService.getUserEmail(USER_ID)).thenReturn("user@example.com");
        when(identityService.getUserPreferredLanguage(USER_ID)).thenReturn("hu");
        when(templateRenderer.renderAlertSubject(any(), any())).thenReturn("Subject");
        when(templateRenderer.renderAlertBody(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("<html>body</html>");
        when(resendEmailSender.send(anyString(), anyString(), anyString())).thenReturn(false);
        when(notificationRepository.countPendingTotal()).thenReturn(0);
        when(notificationRepository.countFailedTotal()).thenReturn(1);

        // When
        processor.processOutbox();

        // Then — marked as FAILED
        verify(notificationRepository).updateOutboxFailed(RECORD_ID);
        verify(notificationRepository, never()).updateOutboxRetry(any(), anyInt(), any());
    }

    @Test
    void processOutbox_demoMode_skipsResendCallButMarksSent() {
        // Given — email.enabled = false (demo mode)
        properties.getEmail().setEnabled(false);
        OutboxRecord record = createAlertRecord(0);
        when(notificationRepository.findPendingOutboxRecords(50)).thenReturn(List.of(record));
        when(identityService.getUserEmail(USER_ID)).thenReturn("user@example.com");
        when(identityService.getUserPreferredLanguage(USER_ID)).thenReturn("hu");
        when(templateRenderer.renderAlertSubject(any(), any())).thenReturn("Subject");
        when(templateRenderer.renderAlertBody(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("<html>body</html>");
        when(notificationRepository.countPendingTotal()).thenReturn(0);
        when(notificationRepository.countFailedTotal()).thenReturn(0);

        // When
        processor.processOutbox();

        // Then — marked as SENT without calling Resend
        verify(notificationRepository).updateOutboxSent(RECORD_ID);
        verify(resendEmailSender, never()).send(anyString(), anyString(), anyString());
        verify(healthState).recordRun(1, 0, 0);
    }

    @Test
    void processOutbox_nullEmail_retriesWithoutSending() {
        // Given — user email cannot be resolved (null)
        OutboxRecord record = createAlertRecord(0);
        when(notificationRepository.findPendingOutboxRecords(50)).thenReturn(List.of(record));
        when(identityService.getUserEmail(USER_ID)).thenReturn(null);
        when(notificationRepository.countPendingTotal()).thenReturn(1);
        when(notificationRepository.countFailedTotal()).thenReturn(0);

        // When
        processor.processOutbox();

        // Then — retry triggered because email couldn't be resolved, no send attempted
        verify(notificationRepository, never()).updateOutboxSent(any());
        verify(resendEmailSender, never()).send(anyString(), anyString(), anyString());
        // Verify handleRetry was actually called (retry count incremented)
        verify(notificationRepository).updateOutboxRetry(eq(RECORD_ID), eq(1), any(OffsetDateTime.class));
    }

    @Test
    void processOutbox_batchProcessingRespectsLimit() {
        // Given
        properties.getOutbox().setBatchSize(10);
        when(notificationRepository.findPendingOutboxRecords(10)).thenReturn(List.of());
        when(notificationRepository.countPendingTotal()).thenReturn(0);
        when(notificationRepository.countFailedTotal()).thenReturn(0);

        // When
        processor.processOutbox();

        // Then — query uses configured batch size
        verify(notificationRepository).findPendingOutboxRecords(10);
    }

    @Test
    void processOutbox_emptyQueue_isNoOp() {
        // Given
        when(notificationRepository.findPendingOutboxRecords(50)).thenReturn(List.of());
        when(notificationRepository.countPendingTotal()).thenReturn(0);
        when(notificationRepository.countFailedTotal()).thenReturn(0);

        // When
        processor.processOutbox();

        // Then — health state updated but no processing
        verify(healthState).recordRun(0, 0, 0);
        verify(notificationRepository, never()).updateOutboxSent(any());
        verify(notificationRepository, never()).updateOutboxRetry(any(), anyInt(), any());
        verify(notificationRepository, never()).updateOutboxFailed(any());
    }
}
