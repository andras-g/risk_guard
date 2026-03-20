package hu.riskguard.notification.api.dto;

import hu.riskguard.notification.domain.FlightControlTenantSummary;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API response DTO for a single client tenant in the Flight Control dashboard.
 * Mirrors {@link FlightControlTenantSummary} with a static factory for controller use.
 *
 * @param tenantId        the client tenant's UUID
 * @param tenantName      human-readable tenant name
 * @param reliableCount   number of watchlist partners with RELIABLE status
 * @param atRiskCount     number of partners with AT_RISK or TAX_SUSPENDED status
 * @param staleCount      number of partners with UNAVAILABLE status (treated as stale)
 * @param incompleteCount number of partners with INCOMPLETE status
 * @param totalPartners   total watchlist entries for this tenant
 * @param lastCheckedAt   most recent last_checked_at across all tenant entries (nullable)
 */
public record FlightControlTenantSummaryResponse(
        UUID tenantId,
        String tenantName,
        int reliableCount,
        int atRiskCount,
        int staleCount,
        int incompleteCount,
        int totalPartners,
        OffsetDateTime lastCheckedAt
) {

    /**
     * Factory method from domain type — the canonical way to create response DTOs.
     * Controllers MUST use this factory, never direct DTO construction.
     *
     * @param domain the domain flight control tenant summary from the service facade
     * @return the API response DTO
     */
    public static FlightControlTenantSummaryResponse from(FlightControlTenantSummary domain) {
        return new FlightControlTenantSummaryResponse(
                domain.tenantId(),
                domain.tenantName(),
                domain.reliableCount(),
                domain.atRiskCount(),
                domain.staleCount(),
                domain.incompleteCount(),
                domain.totalPartners(),
                domain.lastCheckedAt());
    }
}
