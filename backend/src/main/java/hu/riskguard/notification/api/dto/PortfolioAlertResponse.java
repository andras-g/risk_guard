package hu.riskguard.notification.api.dto;

import hu.riskguard.notification.domain.PortfolioAlert;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a portfolio alert — a cross-tenant status change event.
 * Used by the Portfolio Pulse feed to show recent partner risk changes across
 * all tenants an accountant has active mandates for.
 *
 * @param alertId        outbox record UUID
 * @param tenantId       tenant where the status change occurred
 * @param tenantName     human-readable tenant name (resolved via JOIN)
 * @param taxNumber      Hungarian tax number of the partner
 * @param companyName    partner company name
 * @param previousStatus previous verdict status (e.g., RELIABLE)
 * @param newStatus      new verdict status (e.g., AT_RISK)
 * @param changedAt      when the status change occurred
 * @param sha256Hash     audit hash of the verdict (null for digest-sourced alerts)
 * @param verdictId      verdict UUID (null for digest-sourced alerts)
 */
public record PortfolioAlertResponse(
        UUID alertId,
        UUID tenantId,
        String tenantName,
        String taxNumber,
        String companyName,
        String previousStatus,
        String newStatus,
        OffsetDateTime changedAt,
        String sha256Hash,
        UUID verdictId
) {

    /**
     * Factory method from domain type — the canonical way to create response DTOs.
     * Controllers MUST use this factory, never direct DTO construction.
     *
     * @param alert the domain portfolio alert from the service facade
     * @return the API response DTO
     */
    public static PortfolioAlertResponse from(PortfolioAlert alert) {
        return new PortfolioAlertResponse(
                alert.alertId(),
                alert.tenantId(),
                alert.tenantName(),
                alert.taxNumber(),
                alert.companyName(),
                alert.previousStatus(),
                alert.newStatus(),
                alert.changedAt(),
                alert.sha256Hash(),
                alert.verdictId());
    }
}
