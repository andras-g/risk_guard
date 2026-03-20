package hu.riskguard.notification.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain record representing a cross-tenant portfolio alert — a partner status change
 * event aggregated across all tenants an accountant has active mandates for.
 *
 * <p>Sourced from {@code notification_outbox} records with {@code type IN ('ALERT','DIGEST')}
 * and {@code status = 'SENT'}. DIGEST records are expanded into individual alerts.
 *
 * <p>This is the public domain type — controllers and DTOs use this instead of
 * internal repository records.
 *
 * @param alertId        outbox record UUID (or synthetic UUID for digest-expanded items)
 * @param tenantId       tenant where the status change occurred
 * @param tenantName     human-readable tenant name (resolved via JOIN on tenants table)
 * @param taxNumber      Hungarian tax number of the partner
 * @param companyName    partner company name
 * @param previousStatus previous verdict status (e.g., RELIABLE, AT_RISK)
 * @param newStatus      new verdict status
 * @param changedAt      when the status change occurred
 * @param sha256Hash     audit hash of the verdict (null for digest-sourced alerts)
 * @param verdictId      verdict UUID (null for digest-sourced alerts)
 */
public record PortfolioAlert(
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
) {}
