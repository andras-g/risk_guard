package hu.riskguard.epr.api.dto;

/**
 * Response for {@code GET/PATCH /api/v1/epr/producer-profile/default-epr-scope} — Story 10.11 AC #9.
 */
public record DefaultEprScopeResponse(String defaultScope) {

    public static DefaultEprScopeResponse from(String scope) {
        return new DefaultEprScopeResponse(scope);
    }
}
