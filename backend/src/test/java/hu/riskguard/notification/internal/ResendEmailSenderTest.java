package hu.riskguard.notification.internal;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import hu.riskguard.core.config.RiskGuardProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResendEmailSender}.
 * Covers: missing API key graceful degradation, null/blank recipient handling,
 * successful send (via mocked Resend SDK), and ResendException failure path.
 *
 * <p>Tests (a) and (b) from AC10/TEST-2 use Mockito's inline mock maker (Mockito 5.x default)
 * to mock the final {@code Emails} class from the Resend SDK, since the SDK uses a hardcoded
 * base URL ({@code https://api.resend.com}) that cannot be redirected to WireMock.
 */
@ExtendWith(MockitoExtension.class)
class ResendEmailSenderTest {

    @Test
    void missingApiKey_returnsFalseWithoutCallingSDK() {
        // Given — API key is blank
        RiskGuardProperties props = new RiskGuardProperties();
        props.getEmail().setResendApiKey("");
        ResendEmailSender sender = new ResendEmailSender(props);

        // When
        boolean result = sender.send("user@example.com", "Subject", "<html>body</html>");

        // Then
        assertThat(result).isFalse();
        assertThat(sender.isAvailable()).isFalse();
    }

    @Test
    void nullApiKey_returnsFalseWithoutCallingSDK() {
        // Given — API key is null
        RiskGuardProperties props = new RiskGuardProperties();
        props.getEmail().setResendApiKey(null);
        ResendEmailSender sender = new ResendEmailSender(props);

        // When
        boolean result = sender.send("user@example.com", "Subject", "<html>body</html>");

        // Then
        assertThat(result).isFalse();
        assertThat(sender.isAvailable()).isFalse();
    }

    @Test
    void nullRecipient_returnsFalse() {
        // Given
        RiskGuardProperties props = new RiskGuardProperties();
        props.getEmail().setResendApiKey("");
        ResendEmailSender sender = new ResendEmailSender(props);

        // When
        boolean result = sender.send(null, "Subject", "<html>body</html>");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void blankRecipient_returnsFalse() {
        // Given
        RiskGuardProperties props = new RiskGuardProperties();
        props.getEmail().setResendApiKey("");
        ResendEmailSender sender = new ResendEmailSender(props);

        // When
        boolean result = sender.send("  ", "Subject", "<html>body</html>");

        // Then
        assertThat(result).isFalse();
    }

    // --- TEST-2(a): Successful send returns true (H4 review fix) ---

    @Test
    void successfulSend_returnsTrue() throws ResendException {
        // Given — mock Resend SDK to simulate successful API call
        Resend mockResend = mock(Resend.class);
        Emails mockEmails = mock(Emails.class);
        when(mockResend.emails()).thenReturn(mockEmails);

        CreateEmailResponse mockResponse = mock(CreateEmailResponse.class);
        when(mockResponse.getId()).thenReturn("email-id-123");
        when(mockEmails.send(any(CreateEmailOptions.class))).thenReturn(mockResponse);

        ResendEmailSender sender = new ResendEmailSender(mockResend, "alerts@riskguard.hu");

        // When
        boolean result = sender.send("user@example.com", "Subject", "<html>body</html>");

        // Then
        assertThat(result).isTrue();
        assertThat(sender.isAvailable()).isTrue();
    }

    // --- TEST-2(b): ResendException returns false (H4 review fix) ---

    @Test
    void resendException_returnsFalse() throws ResendException {
        // Given — mock Resend SDK to simulate API error
        Resend mockResend = mock(Resend.class);
        Emails mockEmails = mock(Emails.class);
        when(mockResend.emails()).thenReturn(mockEmails);
        when(mockEmails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("API rate limit exceeded"));

        ResendEmailSender sender = new ResendEmailSender(mockResend, "alerts@riskguard.hu");

        // When
        boolean result = sender.send("user@example.com", "Subject", "<html>body</html>");

        // Then
        assertThat(result).isFalse();
        assertThat(sender.isAvailable()).isTrue();
    }
}
