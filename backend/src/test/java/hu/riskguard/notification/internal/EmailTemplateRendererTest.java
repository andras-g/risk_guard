package hu.riskguard.notification.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EmailTemplateRenderer}.
 * Covers: Hungarian/English localization, SHA-256 hash inclusion, tax number masking,
 * disclaimer text presence.
 */
class EmailTemplateRendererTest {

    private EmailTemplateRenderer renderer;
    private static final Locale HU = Locale.forLanguageTag("hu");
    private static final Locale EN = Locale.forLanguageTag("en");

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        renderer = new EmailTemplateRenderer(messageSource);
    }

    @Test
    void renderAlertSubject_hungarian_usesHuTemplate() {
        String subject = renderer.renderAlertSubject("Test Kft", HU);
        assertThat(subject).contains("Test Kft");
        // Hungarian subject contains "állapotváltozás" (status change)
        assertThat(subject).containsIgnoringCase("llapotv");
    }

    @Test
    void renderAlertSubject_english_usesEnTemplate() {
        String subject = renderer.renderAlertSubject("Test Ltd", EN);
        assertThat(subject).contains("Test Ltd");
        assertThat(subject).containsIgnoringCase("status changed");
    }

    @Test
    void renderAlertBody_hungarian_includesAllFields() {
        OffsetDateTime changedAt = OffsetDateTime.of(2026, 3, 20, 10, 0, 0, 0, ZoneOffset.UTC);
        String body = renderer.renderAlertBody(
                "Test Kft", "12345678", "RELIABLE", "AT_RISK",
                changedAt, "sha256hashvalue123", HU);

        assertThat(body).contains("Test Kft");
        assertThat(body).contains("1234****"); // masked tax number
        assertThat(body).contains("RELIABLE");
        assertThat(body).contains("AT_RISK");
        assertThat(body).contains("sha256hashvalue123");
    }

    @Test
    void renderAlertBody_english_includesAllFields() {
        OffsetDateTime changedAt = OffsetDateTime.of(2026, 3, 20, 10, 0, 0, 0, ZoneOffset.UTC);
        String body = renderer.renderAlertBody(
                "Test Ltd", "12345678", "RELIABLE", "AT_RISK",
                changedAt, "abc123hash", EN);

        assertThat(body).contains("Test Ltd");
        assertThat(body).contains("1234****"); // masked tax number
        assertThat(body).contains("abc123hash");
    }

    @Test
    void renderAlertBody_inclusSha256Hash() {
        String body = renderer.renderAlertBody(
                "Kft", "12345678", "OLD", "NEW",
                OffsetDateTime.now(), "0xDEADBEEF", HU);
        assertThat(body).contains("0xDEADBEEF");
    }

    @Test
    void renderAlertBody_taxNumberIsMasked() {
        String body = renderer.renderAlertBody(
                "Kft", "98765432", "OLD", "NEW",
                OffsetDateTime.now(), "hash", EN);
        assertThat(body).contains("9876****");
        assertThat(body).doesNotContain("98765432");
    }

    @Test
    void renderAlertBody_disclaimerTextPresent() {
        String body = renderer.renderAlertBody(
                "Kft", "12345678", "OLD", "NEW",
                OffsetDateTime.now(), "hash", EN);
        assertThat(body).containsIgnoringCase("informational purposes only");
    }

    @Test
    void renderAlertBody_hungarianDisclaimer() {
        String body = renderer.renderAlertBody(
                "Kft", "12345678", "OLD", "NEW",
                OffsetDateTime.now(), "hash", HU);
        // Hungarian disclaimer contains "tájékoztató jellegű"
        assertThat(body).containsIgnoringCase("koztató jelleg");
    }

    @Test
    void renderDigestBody_containsSummaryTable() {
        List<Map<String, String>> changes = List.of(
                Map.of("companyName", "Kft A", "taxNumber", "11111111",
                        "previousStatus", "RELIABLE", "newStatus", "AT_RISK"),
                Map.of("companyName", "Kft B", "taxNumber", "22222222",
                        "previousStatus", "INCOMPLETE", "newStatus", "RELIABLE"));

        String body = renderer.renderDigestBody(changes, HU);
        assertThat(body).contains("Kft A");
        assertThat(body).contains("Kft B");
        assertThat(body).contains("1111****");
        assertThat(body).contains("2222****");
        assertThat(body).contains("<table");
    }

    @Test
    void renderDigestSubject_includesCount() {
        String subject = renderer.renderDigestSubject(5, EN);
        assertThat(subject).contains("5");
    }
}
