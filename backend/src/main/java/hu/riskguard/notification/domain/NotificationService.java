package hu.riskguard.notification.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import hu.riskguard.core.config.RiskGuardProperties;

import hu.riskguard.core.util.PiiUtil;
import hu.riskguard.identity.domain.IdentityService;
import hu.riskguard.notification.internal.NotificationRepository;
import hu.riskguard.notification.internal.NotificationRepository.OutboxRecord;
import hu.riskguard.notification.internal.NotificationRepository.PortfolioOutboxRecord;
import hu.riskguard.notification.internal.NotificationRepository.WatchlistEntryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Module facade for the notification module.
 * This is the ONLY public entry point into the notification module's business logic.
 *
 * <p>External modules call facade methods here — never the repository directly.
 * Follows the module facade pattern: Controller → NotificationService → NotificationRepository.
 *
 * <p>Called by:
 * <ul>
 *   <li>{@code WatchlistController} — tenant-scoped CRUD (add, list, remove, count)</li>
 *   <li>{@code AsyncIngestor} (screening module) — cross-tenant partner list for data refresh</li>
 *   <li>{@code WatchlistMonitor} (screening module) — cross-tenant partner list with verdicts, verdict updates</li>
 * </ul>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final IdentityService identityService;
    private final RiskGuardProperties properties;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository,
                               IdentityService identityService,
                               RiskGuardProperties properties) {
        this.notificationRepository = notificationRepository;
        this.identityService = identityService;
        this.properties = properties;
        // TODO [M2-partial]: Inject Spring-managed ObjectMapper when JacksonAutoConfiguration is enabled
        // (currently no ObjectMapper bean exists in this project; all modules use local instances)
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Get all actively monitored partners across all tenants.
     * Used by the background {@code AsyncIngestor} (screening module) to refresh partner data.
     *
     * <p><b>PRIVILEGED CROSS-TENANT READ:</b> Returns ALL watchlist entries across tenants.
     * Only call from background jobs — never from user-facing code paths.
     *
     * @return list of {@code WatchlistPartner} records with tenant ID + tax number
     */
    public List<WatchlistPartner> getMonitoredPartners() {
        return notificationRepository.findAllWatchlistEntries();
    }

    /**
     * Get all actively monitored partners with their last known verdict status.
     * Used by the background {@code WatchlistMonitor} to compare old vs new verdict.
     *
     * <p><b>PRIVILEGED CROSS-TENANT READ:</b> Returns ALL watchlist entries across tenants
     * including denormalized verdict status for change detection.
     * Only call from the WatchlistMonitor — never from user-facing code paths.
     *
     * @return list of {@code MonitoredPartner} records with tenant ID, tax number, and last verdict status
     */
    public List<MonitoredPartner> getMonitoredPartnersWithVerdicts() {
        return notificationRepository.findAllMonitoredPartners();
    }

    // --- Flight Control (Story 3.10) ---

    /**
     * Get the accountant's cross-tenant Flight Control summary.
     *
     * <p>Aggregates watchlist entries across ALL tenants the accountant has active mandates for.
     * Returns per-tenant verdict status counts and portfolio-wide totals, sorted by
     * atRiskCount DESC, then staleCount DESC (most critical clients first).
     *
     * <p><b>PRIVILEGED CROSS-TENANT READ — accountant mandate-scoped:</b>
     * Authorization is enforced via mandate check; does NOT use TenantFilter context.
     *
     * @param userId the accountant's user ID (resolved from JWT sub claim by controller)
     * @return domain object with per-tenant summaries and portfolio totals
     */
    @Transactional(readOnly = true)
    public FlightControlResult getFlightControlSummary(UUID userId) {
        // Step 1: Resolve all mandated tenant IDs (mandate validity enforced by identityService)
        List<UUID> tenantIds = identityService.getActiveMandateTenantIds(userId);

        // Step 2: Resolve tenant names by querying the tenants table directly
        // (avoids importing identity module DTOs from identity.api.dto)
        Map<UUID, String> tenantNames = notificationRepository.findTenantNamesByIds(tenantIds);

        // Build zero-entry summaries for every mandated tenant (tenants with no watchlist still appear)
        Map<UUID, FlightControlTenantSummary.Builder> builders = new java.util.LinkedHashMap<>();
        for (UUID tenantId : tenantIds) {
            builders.put(tenantId, new FlightControlTenantSummary.Builder(
                    tenantId, tenantNames.getOrDefault(tenantId, "Unknown")));
        }

        // Step 3: Aggregate watchlist entries by tenant and status (cross-tenant read)
        if (!tenantIds.isEmpty()) {
            List<NotificationRepository.WatchlistAggregateRow> rows =
                    notificationRepository.aggregateWatchlistByTenant(tenantIds);

            for (NotificationRepository.WatchlistAggregateRow row : rows) {
                FlightControlTenantSummary.Builder builder = builders.get(row.tenantId());
                if (builder == null) continue; // safety guard

                // Map status to correct count bucket
                // staleCount: UNAVAILABLE entries (no confidence column — treat UNAVAILABLE as stale)
                // atRiskCount: AT_RISK + TAX_SUSPENDED entries
                // incompleteCount: INCOMPLETE entries
                // reliableCount: RELIABLE entries
                String status = row.lastVerdictStatus();
                if (status == null) {
                    builder.addIncomplete(row.count());
                } else {
                    switch (status) {
                        case "RELIABLE" -> builder.addReliable(row.count());
                        case "AT_RISK", "TAX_SUSPENDED" -> builder.addAtRisk(row.count());
                        case "UNAVAILABLE" -> builder.addStale(row.count());
                        case "INCOMPLETE" -> builder.addIncomplete(row.count());
                        default -> builder.addIncomplete(row.count());
                    }
                }

                // Track MAX last_checked_at for this tenant
                if (row.lastChecked() != null) {
                    builder.updateLastChecked(row.lastChecked());
                }
            }
        }

        // Build sorted per-tenant summaries: atRiskCount DESC, then staleCount DESC
        List<FlightControlTenantSummary> tenants = builders.values().stream()
                .map(FlightControlTenantSummary.Builder::build)
                .sorted((a, b) -> {
                    if (b.atRiskCount() != a.atRiskCount()) return Integer.compare(b.atRiskCount(), a.atRiskCount());
                    return Integer.compare(b.staleCount(), a.staleCount());
                })
                .collect(Collectors.toList());

        // Compute portfolio-wide totals
        int totalAtRisk = tenants.stream().mapToInt(FlightControlTenantSummary::atRiskCount).sum();
        int totalStale = tenants.stream().mapToInt(FlightControlTenantSummary::staleCount).sum();
        int totalPartners = tenants.stream().mapToInt(FlightControlTenantSummary::totalPartners).sum();

        return new FlightControlResult(tenants, tenants.size(), totalAtRisk, totalStale, totalPartners);
    }

    /**
     * Result container for the Flight Control summary.
     * Separates domain list from the totals to avoid needing a separate domain class.
     *
     * @param tenants       per-tenant summaries sorted by risk
     * @param totalClients  number of active mandate tenants
     * @param totalAtRisk   sum of atRiskCount across all tenants
     * @param totalStale    sum of staleCount across all tenants
     * @param totalPartners sum of totalPartners across all tenants
     */
    public record FlightControlResult(
            List<FlightControlTenantSummary> tenants,
            int totalClients,
            int totalAtRisk,
            int totalStale,
            int totalPartners
    ) {}

    // --- Portfolio Alerts (Story 3.9) ---

    /**
     * Resolve the accountant's user UUID from JWT email (sub claim).
     * Encapsulates cross-module call to {@link IdentityService} within the notification facade,
     * so the controller does not need to import identity module types.
     *
     * @param email the accountant's email from JWT sub claim
     * @return the user's UUID
     * @throws IllegalArgumentException if email is null/blank or user not found
     */
    public UUID resolveUserIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be null or blank");
        }
        return identityService.findUserByEmail(email)
                .map(user -> user.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found for email"));
    }

    /**
     * Get portfolio alerts across all tenants the accountant has active mandates for.
     * This is a PRIVILEGED CROSS-TENANT READ — authorization is via mandate check.
     *
     * <p>Queries {@code notification_outbox} records with {@code type IN ('ALERT','DIGEST')}
     * and {@code status = 'SENT'} for all mandated tenants. DIGEST records are expanded
     * into individual alert items (one per change entry).
     *
     * @param userId the accountant's user ID (from JWT)
     * @param days   number of days to look back (clamped 1-30)
     * @return list of portfolio alerts, ordered by changedAt DESC with Morning Risk Pulse priority
     */
    @Transactional(readOnly = true)
    public List<PortfolioAlert> getPortfolioAlerts(UUID userId, int days) {
        int clampedDays = Math.max(1, Math.min(30, days));
        OffsetDateTime since = OffsetDateTime.now().minusDays(clampedDays);

        List<UUID> tenantIds = identityService.getActiveMandateTenantIds(userId);
        if (tenantIds.isEmpty()) {
            return List.of();
        }

        List<PortfolioOutboxRecord> raw = notificationRepository.findPortfolioAlerts(tenantIds, since);
        List<PortfolioAlert> alerts = new ArrayList<>();

        for (PortfolioOutboxRecord rec : raw) {
            try {
                if ("ALERT".equals(rec.type())) {
                    alerts.add(parseAlertRecord(rec));
                } else if ("DIGEST".equals(rec.type())) {
                    alerts.addAll(parseDigestRecord(rec));
                }
            } catch (Exception e) {
                log.warn("Failed to parse outbox payload for record {}", rec.id(), e);
            }
        }

        // Morning Risk Pulse priority: AT_RISK first, then STALE/INCOMPLETE, then RELIABLE
        alerts.sort((a, b) -> {
            int priorityA = statusPriority(a.newStatus());
            int priorityB = statusPriority(b.newStatus());
            if (priorityA != priorityB) return Integer.compare(priorityA, priorityB);
            // Within same priority, most recent first
            return b.changedAt().compareTo(a.changedAt());
        });

        return alerts;
    }

    private PortfolioAlert parseAlertRecord(PortfolioOutboxRecord rec) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(rec.payload(), Map.class);
        return new PortfolioAlert(
                rec.id(),
                rec.tenantId(),
                rec.tenantName(),
                (String) payload.get("taxNumber"),
                (String) payload.get("companyName"),
                (String) payload.get("previousStatus"),
                (String) payload.get("newStatus"),
                parseChangedAt(payload.get("changedAt"), rec.createdAt()),
                (String) payload.get("sha256Hash"),
                parseUuid(payload.get("verdictId")));
    }

    private List<PortfolioAlert> parseDigestRecord(PortfolioOutboxRecord rec) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(rec.payload(), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> changes = (List<Map<String, String>>) payload.get("changes");
        if (changes == null || changes.isEmpty()) {
            return List.of();
        }

        List<PortfolioAlert> alerts = new ArrayList<>();
        for (Map<String, String> change : changes) {
            alerts.add(new PortfolioAlert(
                    UUID.randomUUID(),  // Each digest entry gets a unique alertId to avoid duplicate Vue :key values
                    rec.tenantId(),
                    rec.tenantName(),
                    change.get("taxNumber"),
                    change.get("companyName"),
                    change.get("previousStatus"),
                    change.get("newStatus"),
                    rec.createdAt(),  // DIGEST entries use the outbox record's created_at
                    null,             // DIGEST entries lack sha256Hash
                    null));           // DIGEST entries lack verdictId
        }
        return alerts;
    }

    private OffsetDateTime parseChangedAt(Object changedAtObj, OffsetDateTime fallback) {
        if (changedAtObj instanceof String str && !str.isBlank()) {
            try {
                return OffsetDateTime.parse(str);
            } catch (Exception e) {
                return fallback;
            }
        }
        return fallback;
    }

    private UUID parseUuid(Object uuidObj) {
        if (uuidObj instanceof String str && !str.isBlank()) {
            try {
                return UUID.fromString(str);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Morning Risk Pulse priority ordering.
     * Lower number = higher priority (shown first).
     */
    private static int statusPriority(String status) {
        if (status == null) return 3;
        return switch (status) {
            case "AT_RISK" -> 0;
            case "STALE", "INCOMPLETE" -> 1;
            case "RELIABLE" -> 2;
            default -> 3;
        };
    }

    // --- Tenant-Scoped CRUD (Story 3.6) ---

    /**
     * Add a partner to the tenant's watchlist.
     * Prevents duplicates — returns existing entry if tax number already on watchlist.
     *
     * @param tenantId      current tenant from JWT
     * @param taxNumber     Hungarian tax number (normalized)
     * @param companyName   company name from screening (may be null)
     * @param verdictStatus current verdict status from screening (may be null)
     * @return the watchlist entry (new or existing if duplicate), and whether it was a duplicate
     */
    @Transactional
    public AddResult addToWatchlist(UUID tenantId, String taxNumber, String companyName, String verdictStatus) {
        String normalizedTaxNumber = taxNumber.replaceAll("[\\s-]", "");

        // Duplicate check
        Optional<WatchlistEntryRecord> existing =
                notificationRepository.findByTenantIdAndTaxNumber(tenantId, normalizedTaxNumber);
        if (existing.isPresent()) {
            log.info("Watchlist duplicate prevented for tax_number in tenant");
            return new AddResult(toDomain(existing.get()), true);
        }

        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        notificationRepository.insertEntry(id, tenantId, normalizedTaxNumber, companyName, null);

        // Populate denormalized verdict columns immediately so the UI shows
        // verdict status without waiting for the next WatchlistMonitor cycle.
        if (verdictStatus != null && !verdictStatus.isBlank()) {
            notificationRepository.updateVerdictStatus(tenantId, normalizedTaxNumber, verdictStatus, now);
        }

        WatchlistEntry inserted = new WatchlistEntry(
                id, tenantId, normalizedTaxNumber, companyName, null, now, now, verdictStatus, now, null);

        return new AddResult(inserted, false);
    }

    /**
     * Get all watchlist entries for a tenant.
     *
     * @param tenantId current tenant from JWT
     * @return domain watchlist entries
     */
    @Transactional(readOnly = true)
    public List<WatchlistEntry> getWatchlistEntries(UUID tenantId) {
        return notificationRepository.findByTenantId(tenantId).stream()
                .map(NotificationService::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Remove a partner from the tenant's watchlist.
     * Verifies tenant ownership — returns false if entry not found or not owned.
     *
     * @param tenantId current tenant from JWT
     * @param entryId  watchlist entry UUID to remove
     * @return true if deleted, false if not found (returns 404 to avoid info leakage)
     */
    @Transactional
    public boolean removeFromWatchlist(UUID tenantId, UUID entryId) {
        int deleted = notificationRepository.deleteByIdAndTenantId(entryId, tenantId);
        return deleted > 0;
    }

    /**
     * Get watchlist entry count for a tenant — used by sidebar badge.
     *
     * @param tenantId current tenant from JWT
     * @return entry count
     */
    @Transactional(readOnly = true)
    public int getWatchlistCount(UUID tenantId) {
        return notificationRepository.countByTenantId(tenantId);
    }

    /**
     * Update the denormalized verdict status and last_checked_at on a watchlist entry.
     * Used by the WatchlistMonitor (screening module) after verdict re-evaluation.
     *
     * @param tenantId      tenant owning the entry
     * @param taxNumber     the tax number to update
     * @param verdictStatus new verdict status
     * @param checkedAt     timestamp of the evaluation
     * @return number of rows updated (0 = no matching entry)
     */
    public int updateVerdictStatus(UUID tenantId, String taxNumber, String verdictStatus, OffsetDateTime checkedAt) {
        return notificationRepository.updateVerdictStatus(tenantId, taxNumber, verdictStatus, checkedAt);
    }

    /**
     * Update only the last_checked_at timestamp without changing verdict status.
     * Used by WatchlistMonitor when a transient failure occurs or no verdict exists.
     *
     * @param tenantId  tenant owning the entry
     * @param taxNumber the tax number to update
     * @param checkedAt timestamp of the monitoring attempt
     * @return number of rows updated
     */
    public int updateCheckedAt(UUID tenantId, String taxNumber, OffsetDateTime checkedAt) {
        return notificationRepository.updateCheckedAt(tenantId, taxNumber, checkedAt);
    }

    // --- Outbox Record Creation (Story 3.8) ---

    /**
     * Create an alert notification outbox record for a partner status change.
     * Handles digest mode gating: if the tenant's daily alert count exceeds the configured
     * {@code maxAlertsPerDayPerTenant}, a DIGEST record is created (or appended to) instead.
     *
     * @param tenantId the tenant receiving the notification
     * @param userId   the user who should receive the email
     * @param payload  the outbox payload map containing: tenantId, taxNumber, companyName,
     *                 previousStatus, newStatus, verdictId, changedAt, sha256Hash
     */
    @Transactional
    public void createAlertNotification(UUID tenantId, UUID userId, Map<String, Object> payload) {
        OffsetDateTime startOfDay = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC);
        int maxAlerts = properties.getRateLimits().getMaxAlertsPerDayPerTenant();
        int todayCount = notificationRepository.countTodayAlertsByTenant(tenantId, startOfDay);

        if (todayCount < maxAlerts) {
            // Under limit: create individual ALERT record
            try {
                String payloadJson = objectMapper.writeValueAsString(payload);
                notificationRepository.insertOutboxRecord(
                        UUID.randomUUID(), tenantId, userId, "ALERT", payloadJson, "PENDING");
                log.debug("Created ALERT outbox record for tenant, tax_number={}",
                        PiiUtil.maskTaxNumber((String) payload.get("taxNumber")));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize outbox payload", e);
            }
        } else {
            // At/over limit: create or append to DIGEST record
            createOrAppendDigest(tenantId, userId, payload, startOfDay);
        }
    }

    private void createOrAppendDigest(UUID tenantId, UUID userId, Map<String, Object> payload,
                                       OffsetDateTime startOfDay) {
        try {
            Optional<OutboxRecord> existingDigest =
                    notificationRepository.findPendingDigestForTenantToday(tenantId, startOfDay);

            Map<String, String> changeEntry = Map.of(
                    "taxNumber", String.valueOf(payload.get("taxNumber")),
                    "companyName", String.valueOf(payload.get("companyName")),
                    "previousStatus", String.valueOf(payload.get("previousStatus")),
                    "newStatus", String.valueOf(payload.get("newStatus")));

            if (existingDigest.isPresent()) {
                // Append to existing digest
                OutboxRecord digest = existingDigest.get();
                @SuppressWarnings("unchecked")
                Map<String, Object> digestPayload = objectMapper.readValue(digest.payload(), Map.class);
                @SuppressWarnings("unchecked")
                List<Map<String, String>> changes = (List<Map<String, String>>) digestPayload.get("changes");
                if (changes == null) {
                    changes = new ArrayList<>();
                }
                changes.add(changeEntry);
                digestPayload.put("changes", changes);
                notificationRepository.updateOutboxPayload(digest.id(), objectMapper.writeValueAsString(digestPayload));
                log.debug("Appended to existing DIGEST outbox record for tenant");
            } else {
                // Create new digest
                Map<String, Object> digestPayload = new LinkedHashMap<>();
                digestPayload.put("tenantId", tenantId.toString());
                digestPayload.put("changes", List.of(changeEntry));
                notificationRepository.insertOutboxRecord(
                        UUID.randomUUID(), tenantId, userId, "DIGEST",
                        objectMapper.writeValueAsString(digestPayload), "PENDING");
                log.debug("Created new DIGEST outbox record for tenant (daily alert limit reached)");
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize digest payload", e);
        }
    }

    /**
     * Get outbox statistics — used by health indicator.
     *
     * @return array of [pendingCount, failedCount]
     */
    public int[] getOutboxStats() {
        return new int[]{
                notificationRepository.countPendingTotal(),
                notificationRepository.countFailedTotal()
        };
    }

    /**
     * Map internal repository record to public domain type.
     */
    private static WatchlistEntry toDomain(WatchlistEntryRecord rec) {
        return new WatchlistEntry(
                rec.id(), rec.tenantId(), rec.taxNumber(), rec.companyName(), rec.label(),
                rec.createdAt(), rec.updatedAt(), rec.verdictStatus(), rec.lastCheckedAt(),
                rec.latestSha256Hash());
    }

    /**
     * Result of adding a watchlist entry.
     * @param entry     the domain entry (new or existing)
     * @param duplicate true if entry already existed (no insert performed)
     */
    public record AddResult(WatchlistEntry entry, boolean duplicate) {}
}
