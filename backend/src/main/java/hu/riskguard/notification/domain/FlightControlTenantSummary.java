package hu.riskguard.notification.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain record representing a single client tenant's watchlist summary for the
 * accountant's Flight Control dashboard.
 *
 * <p>Populated by {@link NotificationService#getFlightControlSummary(UUID)} via
 * cross-tenant aggregation of {@code watchlist_entries} scoped to mandated tenants.
 *
 * @param tenantId        the client tenant's UUID
 * @param tenantName      human-readable tenant name (resolved via JOIN on {@code tenants})
 * @param reliableCount   number of watchlist entries with {@code last_verdict_status = 'RELIABLE'}
 * @param atRiskCount     number of entries with {@code AT_RISK} or {@code TAX_SUSPENDED} status
 * @param staleCount      number of entries with {@code UNAVAILABLE} status (treated as stale)
 * @param incompleteCount number of entries with {@code INCOMPLETE} status
 * @param totalPartners   total watchlist entries for this tenant
 * @param lastCheckedAt   MAX {@code last_checked_at} across all entries for this tenant (nullable)
 */
public record FlightControlTenantSummary(
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
     * Mutable builder for assembling a {@link FlightControlTenantSummary} from
     * multiple aggregate rows (one per verdict status per tenant).
     */
    public static class Builder {
        private final UUID tenantId;
        private final String tenantName;
        private int reliableCount;
        private int atRiskCount;
        private int staleCount;
        private int incompleteCount;
        private OffsetDateTime lastCheckedAt;

        public Builder(UUID tenantId, String tenantName) {
            this.tenantId = tenantId;
            this.tenantName = tenantName;
        }

        public void addReliable(int count)    { this.reliableCount += count; }
        public void addAtRisk(int count)      { this.atRiskCount += count; }
        public void addStale(int count)       { this.staleCount += count; }
        public void addIncomplete(int count)  { this.incompleteCount += count; }

        public void updateLastChecked(OffsetDateTime ts) {
            if (ts == null) {
                return;
            }
            if (this.lastCheckedAt == null || ts.isAfter(this.lastCheckedAt)) {
                this.lastCheckedAt = ts;
            }
        }

        public FlightControlTenantSummary build() {
            int total = reliableCount + atRiskCount + staleCount + incompleteCount;
            return new FlightControlTenantSummary(
                    tenantId, tenantName,
                    reliableCount, atRiskCount, staleCount, incompleteCount,
                    total, lastCheckedAt);
        }
    }
}
