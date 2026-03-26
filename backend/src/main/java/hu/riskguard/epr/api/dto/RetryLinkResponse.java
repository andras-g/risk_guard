package hu.riskguard.epr.api.dto;

/**
 * Response for {@code POST /wizard/retry-link} — result of retry-linking a calculation to a template.
 *
 * @param templateUpdated whether the template was successfully updated with the KF-code
 * @param kfCode          the effective KF-code that was (or would be) linked
 */
public record RetryLinkResponse(
        boolean templateUpdated,
        String kfCode
) {

    public static RetryLinkResponse from(boolean templateUpdated, String kfCode) {
        return new RetryLinkResponse(templateUpdated, kfCode);
    }
}
