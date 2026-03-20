package hu.riskguard.notification.internal;

import hu.riskguard.core.util.PiiUtil;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders localized HTML email content for notification outbox records.
 *
 * <p>Uses Spring's {@link MessageSource} backed by {@code messages_hu.properties} /
 * {@code messages_en.properties} for template strings. Builds HTML programmatically
 * (no Thymeleaf dependency — keep it simple per Dev Notes).
 *
 * <p>This class lives in {@code notification.internal} — it is an infrastructure detail
 * not exposed cross-module.
 */
@Component
public class EmailTemplateRenderer {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");

    private final MessageSource messageSource;

    public EmailTemplateRenderer(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Render the subject line for a status change alert email.
     *
     * @param companyName the company name
     * @param locale      user's preferred locale (hu or en)
     * @return localized subject line
     */
    public String renderAlertSubject(String companyName, Locale locale) {
        return messageSource.getMessage("email.subject.statusChange",
                new Object[]{companyName}, locale);
    }

    /**
     * Render the subject line for a digest email.
     *
     * @param changeCount number of status changes in the digest
     * @param locale      user's preferred locale
     * @return localized digest subject line
     */
    public String renderDigestSubject(int changeCount, Locale locale) {
        return messageSource.getMessage("email.subject.digest",
                new Object[]{changeCount}, locale);
    }

    /**
     * Render the HTML body for a single status change alert email.
     *
     * @param companyName    company name
     * @param taxNumber      raw tax number (will be masked in the email)
     * @param previousStatus previous verdict status
     * @param newStatus      new verdict status
     * @param changedAt      timestamp of the change
     * @param sha256Hash     audit proof hash from the verdict
     * @param locale         user's preferred locale
     * @return HTML email body
     */
    public String renderAlertBody(String companyName, String taxNumber,
                                   String previousStatus, String newStatus,
                                   OffsetDateTime changedAt, String sha256Hash,
                                   Locale locale) {
        String maskedTax = PiiUtil.maskTaxNumber(taxNumber);
        String header = messageSource.getMessage("email.body.header", null, locale);
        String detail = messageSource.getMessage("email.body.statusChange.detail",
                new Object[]{companyName, maskedTax, previousStatus, newStatus,
                        changedAt != null ? changedAt.format(TIMESTAMP_FORMAT) : "N/A"},
                locale);
        String auditHashLabel = messageSource.getMessage("email.body.auditHash",
                new Object[]{sha256Hash != null ? sha256Hash : "N/A"}, locale);
        String disclaimer = messageSource.getMessage("email.body.disclaimer", null, locale);
        String footer = messageSource.getMessage("email.body.footer", null, locale);

        return buildHtml(header, detail, auditHashLabel, disclaimer, footer);
    }

    /**
     * Render the HTML body for a digest email summarizing multiple status changes.
     *
     * @param changes list of change maps, each with keys: companyName, taxNumber, previousStatus, newStatus
     * @param locale  user's preferred locale
     * @return HTML email body
     */
    public String renderDigestBody(List<Map<String, String>> changes, Locale locale) {
        String header = messageSource.getMessage("email.body.header", null, locale);
        String summary = messageSource.getMessage("email.body.digest.summary",
                new Object[]{changes.size()}, locale);
        String disclaimer = messageSource.getMessage("email.body.disclaimer", null, locale);
        String footer = messageSource.getMessage("email.body.footer", null, locale);

        // Localized table headers (AC4: localized email content)
        String thCompany = messageSource.getMessage("email.table.company", null, locale);
        String thTaxNumber = messageSource.getMessage("email.table.taxNumber", null, locale);
        String thPrevious = messageSource.getMessage("email.table.previous", null, locale);
        String thNew = messageSource.getMessage("email.table.new", null, locale);

        StringBuilder tableRows = new StringBuilder();
        for (Map<String, String> change : changes) {
            String maskedTax = PiiUtil.maskTaxNumber(change.get("taxNumber"));
            tableRows.append("<tr>")
                    .append("<td style=\"padding:8px;border:1px solid #ddd;\">")
                    .append(escapeHtml(change.get("companyName"))).append("</td>")
                    .append("<td style=\"padding:8px;border:1px solid #ddd;\">")
                    .append(maskedTax).append("</td>")
                    .append("<td style=\"padding:8px;border:1px solid #ddd;\">")
                    .append(statusBadge(change.get("previousStatus"))).append("</td>")
                    .append("<td style=\"padding:8px;border:1px solid #ddd;\">")
                    .append(statusBadge(change.get("newStatus"))).append("</td>")
                    .append("</tr>");
        }

        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"font-family:Arial,sans-serif;\">"
                + "<h2>" + escapeHtml(header) + "</h2>"
                + "<p>" + escapeHtml(summary) + "</p>"
                + "<table style=\"border-collapse:collapse;width:100%;\">"
                + "<thead><tr>"
                + "<th style=\"padding:8px;border:1px solid #ddd;background:#f4f4f4;\">" + escapeHtml(thCompany) + "</th>"
                + "<th style=\"padding:8px;border:1px solid #ddd;background:#f4f4f4;\">" + escapeHtml(thTaxNumber) + "</th>"
                + "<th style=\"padding:8px;border:1px solid #ddd;background:#f4f4f4;\">" + escapeHtml(thPrevious) + "</th>"
                + "<th style=\"padding:8px;border:1px solid #ddd;background:#f4f4f4;\">" + escapeHtml(thNew) + "</th>"
                + "</tr></thead><tbody>"
                + tableRows
                + "</tbody></table>"
                + "<hr style=\"margin:20px 0;\">"
                + "<p style=\"color:#666;font-size:12px;\">" + escapeHtml(disclaimer) + "</p>"
                + "<p style=\"color:#999;font-size:11px;\">" + escapeHtml(footer) + "</p>"
                + "</body></html>";
    }

    private String buildHtml(String header, String detail, String auditHash,
                              String disclaimer, String footer) {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"font-family:Arial,sans-serif;\">"
                + "<h2>" + escapeHtml(header) + "</h2>"
                + "<p>" + escapeHtml(detail) + "</p>"
                + "<p style=\"font-family:monospace;background:#f4f4f4;padding:8px;border-radius:4px;\">"
                + escapeHtml(auditHash) + "</p>"
                + "<hr style=\"margin:20px 0;\">"
                + "<p style=\"color:#666;font-size:12px;\">" + escapeHtml(disclaimer) + "</p>"
                + "<p style=\"color:#999;font-size:11px;\">" + escapeHtml(footer) + "</p>"
                + "</body></html>";
    }

    private String statusBadge(String status) {
        if (status == null) return "<span>N/A</span>";
        String color = switch (status) {
            case "RELIABLE" -> "#28a745";
            case "AT_RISK" -> "#dc3545";
            case "INCOMPLETE" -> "#ffc107";
            case "UNAVAILABLE" -> "#6c757d";
            default -> "#6c757d";
        };
        return "<span style=\"color:" + color + ";font-weight:bold;\">" + escapeHtml(status) + "</span>";
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
