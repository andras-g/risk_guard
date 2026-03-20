package hu.riskguard.notification.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import hu.riskguard.core.config.OutboxHealthState;
import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.util.PiiUtil;
import hu.riskguard.identity.domain.IdentityService;
import hu.riskguard.notification.internal.EmailTemplateRenderer;
import hu.riskguard.notification.internal.NotificationRepository;
import hu.riskguard.notification.internal.NotificationRepository.OutboxRecord;
import hu.riskguard.notification.internal.ResendEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Polls the {@code notification_outbox} table and sends pending email notifications
 * via the Resend API. Implements exponential backoff on failure and marks records
 * as FAILED after max retries exceeded.
 *
 * <p>Runs every 60 seconds (configurable via {@code risk-guard.outbox.cron}).
 * Each run processes up to {@code batch-size} records (default 50).
 *
 * <p>In demo/test mode ({@code risk-guard.email.enabled=false}), emails are logged
 * but not sent — records are still marked as SENT to validate the full pipeline.
 *
 * @see NotificationRepository
 * @see ResendEmailSender
 * @see EmailTemplateRenderer
 */
@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final NotificationRepository notificationRepository;
    private final IdentityService identityService;
    private final EmailTemplateRenderer templateRenderer;
    private final ResendEmailSender resendEmailSender;
    private final RiskGuardProperties properties;
    private final OutboxHealthState healthState;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public OutboxProcessor(NotificationRepository notificationRepository,
                           IdentityService identityService,
                           EmailTemplateRenderer templateRenderer,
                           ResendEmailSender resendEmailSender,
                           RiskGuardProperties properties,
                           OutboxHealthState healthState) {
        this.notificationRepository = notificationRepository;
        this.identityService = identityService;
        this.templateRenderer = templateRenderer;
        this.resendEmailSender = resendEmailSender;
        this.properties = properties;
        this.healthState = healthState;
    }

    @Scheduled(cron = "${risk-guard.outbox.cron:0 */1 * * * ?}")
    public void processOutbox() {
        List<OutboxRecord> pending = notificationRepository
                .findPendingOutboxRecords(properties.getOutbox().getBatchSize());

        if (pending.isEmpty()) {
            healthState.recordRun(0,
                    notificationRepository.countPendingTotal(),
                    notificationRepository.countFailedTotal());
            return;
        }

        int sent = 0;
        for (OutboxRecord record : pending) {
            try {
                boolean success = processRecord(record);
                if (success) sent++;
            } catch (Exception e) {
                log.error("Outbox processing failed id={}", record.id(), e);
                handleRetry(record);
            }
        }

        healthState.recordRun(sent,
                notificationRepository.countPendingTotal(),
                notificationRepository.countFailedTotal());
    }

    @SuppressWarnings("unchecked")
    private boolean processRecord(OutboxRecord record) {
        String email = identityService.getUserEmail(record.userId());
        if (email == null) {
            log.warn("Cannot resolve email for user_id={} — skipping outbox record id={}",
                    record.userId(), record.id());
            handleRetry(record);
            return false;
        }

        String language = identityService.getUserPreferredLanguage(record.userId());
        Locale locale = language != null ? Locale.forLanguageTag(language) : Locale.forLanguageTag("hu");

        String subject;
        String body;

        try {
            Map<String, Object> payload = objectMapper.readValue(record.payload(), Map.class);

            if ("DIGEST".equals(record.type())) {
                List<Map<String, String>> changes = (List<Map<String, String>>) payload.get("changes");
                subject = templateRenderer.renderDigestSubject(
                        changes != null ? changes.size() : 0, locale);
                body = templateRenderer.renderDigestBody(changes != null ? changes : List.of(), locale);
            } else {
                // ALERT type
                String companyName = (String) payload.get("companyName");
                String taxNumber = (String) payload.get("taxNumber");
                String previousStatus = (String) payload.get("previousStatus");
                String newStatus = (String) payload.get("newStatus");
                String changedAt = (String) payload.get("changedAt");
                String sha256Hash = (String) payload.get("sha256Hash");

                OffsetDateTime changedAtDt = changedAt != null ? OffsetDateTime.parse(changedAt) : null;

                subject = templateRenderer.renderAlertSubject(companyName, locale);
                body = templateRenderer.renderAlertBody(
                        companyName, taxNumber, previousStatus, newStatus,
                        changedAtDt, sha256Hash, locale);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse outbox payload id={}", record.id(), e);
            handleRetry(record);
            return false;
        }

        // Demo mode: log instead of sending
        if (!properties.getEmail().isEnabled()) {
            log.info("[DEMO] Email would be sent to={} subject={}", PiiUtil.maskEmail(email), subject);
            notificationRepository.updateOutboxSent(record.id());
            return true;
        }

        boolean success = resendEmailSender.send(email, subject, body);
        if (success) {
            notificationRepository.updateOutboxSent(record.id());
            return true;
        } else {
            handleRetry(record);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void handleRetry(OutboxRecord record) {
        int newRetryCount = record.retryCount() + 1;
        if (newRetryCount >= properties.getEmail().getMaxRetriesCount()) {
            notificationRepository.updateOutboxFailed(record.id());
            // AC5: WARN log must include tenant and masked tax number
            String maskedTaxNumber = "****";
            try {
                Map<String, Object> payload = objectMapper.readValue(record.payload(), Map.class);
                maskedTaxNumber = PiiUtil.maskTaxNumber((String) payload.get("taxNumber"));
            } catch (Exception e) {
                // Best-effort — log with default mask if payload parsing fails
            }
            log.warn("Email delivery failed permanently tenant={} tax_number={} retries={}",
                    record.tenantId(), maskedTaxNumber, newRetryCount);
        } else {
            // AC5: backoff = 2^retry_count * base_seconds → 30s, 60s, 120s, 240s, 480s
            // Uses record.retryCount() (pre-increment) so first failure = 2^0 * 30 = 30s
            long backoffSeconds = Math.min(
                    (long) Math.pow(2, record.retryCount()) * properties.getEmail().getBaseBackoffSeconds(),
                    properties.getEmail().getMaxBackoffSeconds());
            OffsetDateTime nextRetry = OffsetDateTime.now().plusSeconds(backoffSeconds);
            notificationRepository.updateOutboxRetry(record.id(), newRetryCount, nextRetry);
        }
    }
}
