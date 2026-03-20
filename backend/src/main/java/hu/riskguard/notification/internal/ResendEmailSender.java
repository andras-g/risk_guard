package hu.riskguard.notification.internal;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.util.PiiUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Wraps the Resend Java SDK for sending transactional emails.
 *
 * <p>If the Resend API key is not configured (blank or null), the sender logs a WARN at
 * startup and gracefully degrades: {@link #send} returns {@code false} without calling
 * the API, and outbox records continue to be created for future delivery.
 *
 * <p>This class lives in {@code notification.internal} — it is an infrastructure detail
 * not exposed cross-module.
 *
 * @see hu.riskguard.notification.domain.OutboxProcessor
 */
@Component
public class ResendEmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    private final Resend resend;
    private final boolean available;
    private final String fromAddress;

    @Autowired
    public ResendEmailSender(RiskGuardProperties properties) {
        this(properties.getEmail().getResendApiKey(), properties.getEmail().getFrom());
    }

    /**
     * Package-private constructor for testing — allows injecting a pre-configured API key
     * and from address without requiring the full RiskGuardProperties.
     */
    ResendEmailSender(String apiKey, String fromAddress) {
        this.fromAddress = fromAddress;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Resend API key not configured — email delivery disabled");
            this.resend = null;
            this.available = false;
        } else {
            this.resend = new Resend(apiKey);
            this.available = true;
        }
    }

    /**
     * Package-private constructor for testing — allows injecting a pre-built Resend client
     * (e.g., configured to use WireMock as the API endpoint).
     */
    ResendEmailSender(Resend resend, String fromAddress) {
        this.fromAddress = fromAddress;
        this.resend = resend;
        this.available = resend != null;
    }

    /**
     * Send an email via the Resend API.
     *
     * @param to       recipient email address
     * @param subject  email subject line
     * @param htmlBody HTML email body
     * @return {@code true} if the email was sent successfully, {@code false} on failure or unavailability
     */
    public boolean send(String to, String subject, String htmlBody) {
        if (to == null || to.isBlank()) {
            log.warn("Cannot send email: recipient is null or blank");
            return false;
        }
        if (!available) {
            log.debug("Resend not available, skipping email to={}", PiiUtil.maskEmail(to));
            return false;
        }
        try {
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromAddress)
                    .to(to)
                    .subject(subject)
                    .html(htmlBody)
                    .build();
            CreateEmailResponse response = resend.emails().send(params);
            log.info("Email sent via Resend id={}", response.getId());
            return true;
        } catch (ResendException e) {
            log.error("Resend API error: {}", e.getMessage());
            return false;
        }
    }

    /** @return true if the Resend API key is configured and the client is available */
    public boolean isAvailable() {
        return available;
    }
}
