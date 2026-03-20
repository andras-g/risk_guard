package hu.riskguard.notification.api.dto;

import hu.riskguard.notification.domain.NotificationService;

import java.util.List;

/**
 * Top-level API response DTO for the Flight Control dashboard endpoint.
 * Contains a portfolio-wide {@link TotalsSummary} and a per-client tenant list.
 *
 * @param totals  aggregate totals across all mandated tenants
 * @param tenants per-tenant summaries, ordered by atRiskCount DESC then staleCount DESC
 */
public record FlightControlResponse(
        TotalsSummary totals,
        List<FlightControlTenantSummaryResponse> tenants
) {

    /**
     * Portfolio-wide aggregate totals shown in the Flight Control summary bar.
     *
     * @param totalClients  number of distinct client tenants (active mandates)
     * @param totalAtRisk   sum of atRiskCount across all tenants
     * @param totalStale    sum of staleCount across all tenants
     * @param totalPartners sum of totalPartners across all tenants
     */
    public record TotalsSummary(
            int totalClients,
            int totalAtRisk,
            int totalStale,
            int totalPartners
    ) {}

    /**
     * Factory method from domain result type — the canonical way to create the response DTO.
     * Controllers MUST use this factory, never direct DTO construction.
     *
     * @param result the domain flight control result from the service facade
     * @return the API response DTO
     */
    public static FlightControlResponse from(NotificationService.FlightControlResult result) {
        List<FlightControlTenantSummaryResponse> tenantDtos = result.tenants().stream()
                .map(FlightControlTenantSummaryResponse::from)
                .toList();
        TotalsSummary totals = new TotalsSummary(
                result.totalClients(), result.totalAtRisk(), result.totalStale(), result.totalPartners());
        return new FlightControlResponse(totals, tenantDtos);
    }
}
