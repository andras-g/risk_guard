package hu.riskguard.screening.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.security.TenantContext;
import hu.riskguard.core.util.HashUtil;
import hu.riskguard.jooq.enums.VerdictConfidence;
import hu.riskguard.jooq.enums.VerdictStatus;
import hu.riskguard.datasource.api.dto.CompanyData;
import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.screening.api.dto.ProvenanceResponse;
// PublicCompanyData domain record — returned by getPublicCompanyData(), mapped to DTO by controller
import hu.riskguard.screening.domain.events.PartnerSearchCompleted;
import hu.riskguard.core.events.PartnerStatusChanged;
import hu.riskguard.screening.internal.ScreeningRepository;
import hu.riskguard.screening.internal.ScreeningRepository.AuditHistoryRow;
import hu.riskguard.screening.internal.ScreeningRepository.AuditVerifyRow;
import hu.riskguard.screening.internal.ScreeningRepository.FreshSnapshot;
import hu.riskguard.screening.internal.ScreeningRepository.SnapshotRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service facade for screening operations.
 * This is the ONLY public entry point into the screening module's business logic.
 *
 * <p>Follows the module facade pattern: Controller → ScreeningService → ScreeningRepository.
 * External modules must use this facade (or application events) — never the repository directly.
 */
@Service
public class ScreeningService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningService.class);

    private final ScreeningRepository screeningRepository;
    private final DataSourceService dataSourceService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final FreshnessConfig freshnessConfig;

    /**
     * Idempotency guard threshold — return cached verdict if a fresh snapshot
     * exists within this many minutes. Canonical value in risk-guard-tokens.json.
     */
    private final int freshnessThresholdMinutes;

    /**
     * Legal disclaimer included in the audit log and SHA-256 hash.
     * Canonical value in risk-guard-tokens.json.
     */
    private final String disclaimerText;

    /**
     * Active data source mode (demo/test/live) — recorded per snapshot for audit trail.
     * Sourced from {@code riskguard.data-source.mode} property.
     */
    private final String dataSourceMode;

    public ScreeningService(
            ScreeningRepository screeningRepository,
            DataSourceService dataSourceService,
            ApplicationEventPublisher eventPublisher,
            TransactionTemplate transactionTemplate,
            RiskGuardProperties properties,
            @Value("${risk-guard.screening.idempotency-guard-minutes:15}") int freshnessThresholdMinutes,
            @Value("${risk-guard.screening.disclaimer-text:This search result is provided for informational purposes only. Data is sourced from Hungarian government registries and may not reflect real-time status.}") String disclaimerText) {
        this.screeningRepository = screeningRepository;
        this.dataSourceService = dataSourceService;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.freshnessConfig = new FreshnessConfig(
                properties.getFreshness().getFreshThresholdHours(),
                properties.getFreshness().getStaleThresholdHours(),
                properties.getFreshness().getUnavailableThresholdHours());
        this.freshnessThresholdMinutes = freshnessThresholdMinutes;
        this.disclaimerText = disclaimerText;
        this.dataSourceMode = properties.getDataSource().getMode();
    }

    /**
     * Execute a partner search for the given tax number.
     *
     * <p>Flow:
     * <ol>
     *   <li>Normalize the tax number (strip hyphens/whitespace)</li>
     *   <li>TX1: Check idempotency guard — return cached verdict if fresh snapshot exists (&lt; 15 min)</li>
     *   <li>TX1: Create stub CompanySnapshot with empty snapshot_data JSONB</li>
     *   <li>Scrape government portals in parallel (outside any DB transaction)</li>
     *   <li>TX2: Persist scraped data, compute verdict via VerdictEngine
     *        (RELIABLE / AT_RISK / INCOMPLETE / TAX_SUSPENDED), write audit log</li>
     *   <li>Detect status change vs previous verdict, publish PartnerStatusChanged if changed</li>
     *   <li>Publish PartnerSearchCompleted event</li>
     *   <li>Return SearchResult domain object with real verdict status/confidence/riskSignals</li>
     * </ol>
     *
     * @param taxNumber the Hungarian tax number (8 or 11 digits, may contain hyphens)
     * @param userId    the user performing the search (from JWT)
     * @param tenantId  the tenant initiating the search (from JWT). Used exclusively for
     *                  application event publishing. Repository operations use {@code TenantContext}
     *                  (set by {@code TenantFilter}) for tenant-scoped queries. This separation is
     *                  intentional: events carry explicit tenant IDs for downstream consumers that
     *                  may not share the same thread-local context.
     * @return SearchResult with the search result (mapped to VerdictResponse by controller)
     */
    public SearchResult search(String taxNumber, UUID userId, UUID tenantId) {
        String normalizedTaxNumber = taxNumber.replaceAll("[\\s-]", "");
        OffsetDateTime now = OffsetDateTime.now();

        // TX1: Idempotency guard + create stub snapshot
        // Keeps DB connection open only for the short read + insert — NOT during data source I/O.
        record Tx1Result(UUID snapshotId, SearchResult cachedResult) {}
        Tx1Result tx1 = transactionTemplate.execute(status -> {
            Optional<FreshSnapshot> fresh = screeningRepository.findFreshSnapshot(
                    normalizedTaxNumber, freshnessThresholdMinutes);

            if (fresh.isPresent()) {
                FreshSnapshot cached = fresh.get();
                return new Tx1Result(null, new SearchResult(
                        cached.verdictId(),
                        cached.snapshotId(),
                        normalizedTaxNumber,
                        cached.status(),
                        cached.confidence(),
                        cached.createdAt(),
                        List.of(),  // Cached results return empty riskSignals — see `cached` flag below.
                        true,       // Mark as cached so frontend can distinguish from fresh results.
                        null,       // companyName not available for cached results (snapshot not re-parsed).
                        null        // sha256Hash not returned for cached results (original audit entry).
                ));
            }

            UUID snapshotId = screeningRepository.createSnapshot(normalizedTaxNumber, now);
            return new Tx1Result(snapshotId, null);
        });

        // Return cached result if idempotency guard hit
        if (tx1.cachedResult() != null) {
            return tx1.cachedResult();
        }

        UUID snapshotId = tx1.snapshotId();

        // Fetch data from adapters in parallel — OUTSIDE any DB transaction (pure I/O).
        // Resilience4j circuit breakers and retries handle fault tolerance.
        CompanyData companyData = dataSourceService.fetchCompanyData(normalizedTaxNumber);

        // TX2: Persist scraped data + compute verdict + audit log.
        // Keeps DB connection open only for the short writes — NOT during data source I/O.
        OffsetDateTime checkedAt = OffsetDateTime.now();

        // Serialize snapshot data to JSON string for the audit hash (done outside TX2 to avoid
        // serialization errors inside the transaction boundary).
        // Guard: null snapshotData map must be treated as missing — Jackson serializes null to the
        // string "null" (not an exception), which would produce a valid-looking hash for missing data.
        // A hash over the literal string "null" is legally meaningless and misleading.
        String snapshotDataJson;
        if (companyData.snapshotData() == null) {
            log.warn("Null snapshot data map — will use HASH_UNAVAILABLE sentinel for audit hash");
            snapshotDataJson = null;
        } else {
            try {
                snapshotDataJson = JSONB_MAPPER.writeValueAsString(companyData.snapshotData());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // If serialization fails, pass null — writeAuditLog() will catch the IAE from HashUtil
                // and write HASH_UNAVAILABLE sentinel. This path should never occur for well-formed data.
                log.warn("Failed to serialize snapshot data for audit hash — will use HASH_UNAVAILABLE sentinel");
                snapshotDataJson = null;
            }
        }
        final String snapshotJson = snapshotDataJson;

        record Tx2Result(UUID verdictId, VerdictStatus status, VerdictConfidence confidence,
                         List<String> riskSignals, String companyName, String sha256Hash) {}
        Tx2Result tx2 = transactionTemplate.execute(status -> {
            screeningRepository.updateSnapshotData(
                    snapshotId, companyData.snapshotData(), companyData.sourceUrls(),
                    companyData.domFingerprintHash(), checkedAt, dataSourceMode);

            // Parse raw JSONB into typed domain model and evaluate verdict
            SnapshotData parsedData = SnapshotDataParser.parse(companyData.snapshotData());
            // Use checkedAt consistently as the evaluation time — avoids a third OffsetDateTime.now()
            // call that would create inconsistent timestamps across the audit trail (review finding #3).
            VerdictResult verdictResult = VerdictEngine.evaluate(parsedData, checkedAt, freshnessConfig, checkedAt);

            UUID vId = screeningRepository.createVerdict(snapshotId, verdictResult.status(),
                    verdictResult.confidence(), checkedAt);
            String auditHash = screeningRepository.writeAuditLog(
                    normalizedTaxNumber, userId, disclaimerText,
                    snapshotJson,
                    verdictResult.status().getLiteral(),
                    verdictResult.confidence().getLiteral(),
                    vId,
                    now,
                    "MANUAL",
                    dataSourceMode);

            return new Tx2Result(vId, verdictResult.status(), verdictResult.confidence(),
                    verdictResult.riskSignals(), parsedData.companyName(), auditHash);
        });

        UUID verdictId = tx2.verdictId();

        // Status-change detection — OUTSIDE TX2 boundary (after commit succeeded).
        // Compare the new verdict status with the most recent previous verdict for the same partner+tenant.
        // Only publish PartnerStatusChanged if a previous verdict exists AND the status actually changed.
        // First-ever search establishes the baseline — no "change" event for that.
        // Wrapped in a short TX for explicit isolation guarantees (not relying on auto-commit).
        Optional<VerdictStatus> previousStatus = transactionTemplate.execute(status ->
                screeningRepository.findPreviousVerdict(normalizedTaxNumber, verdictId));
        if (previousStatus != null && previousStatus.isPresent() && previousStatus.get() != tx2.status()) {
            eventPublisher.publishEvent(PartnerStatusChanged.of(
                    verdictId, tenantId, normalizedTaxNumber,
                    previousStatus.get().getLiteral(),
                    tx2.status().getLiteral(),
                    tx2.sha256Hash()));
        }

        // Publish event OUTSIDE the transaction to avoid dispatching events before commit succeeds.
        // Spring's default ApplicationEventPublisher is synchronous — publishing inside TX2 means
        // listeners could run before the DB commit, causing inconsistency if the commit later fails.
        eventPublisher.publishEvent(PartnerSearchCompleted.of(snapshotId, verdictId, tenantId));

        return new SearchResult(
                verdictId,
                snapshotId,
                normalizedTaxNumber,
                tx2.status(),
                tx2.confidence(),
                checkedAt,
                tx2.riskSignals(),
                false,              // Freshly computed — not from cache
                tx2.companyName(),
                tx2.sha256Hash()
        );
    }

    /**
     * Retrieve the latest verdict status for a given tenant and tax number.
     * Used by the {@code WatchlistMonitor} (notification module) to compare the current verdict
     * against the watchlist entry's stored verdict status for change detection.
     *
     * <p>This is a simple read-only facade call — no re-evaluation or data source access.
     * The verdict was already computed by the search flow or AsyncIngestor refresh.
     *
     * @param tenantId  explicit tenant ID (background job context — TenantContext must be set by caller)
     * @param taxNumber normalized tax number
     * @return the latest verdict data, or {@code null} if no verdict exists for this partner
     */
    public SnapshotVerdictResult getLatestSnapshotWithVerdict(UUID tenantId, String taxNumber) {
        return screeningRepository.findLatestVerdictByTenantAndTaxNumber(tenantId, taxNumber)
                .map(rec -> {
                    boolean transient_ = rec.status() == VerdictStatus.INCOMPLETE
                            || rec.status() == VerdictStatus.UNAVAILABLE;
                    return new SnapshotVerdictResult(
                            rec.verdictId(),
                            rec.status().getLiteral(),
                            rec.createdAt(),
                            transient_
                    );
                })
                .orElse(null);
    }

    /**
     * Result of looking up the latest snapshot + verdict for a partner.
     * Used by the WatchlistMonitor for status change detection.
     *
     * @param verdictId       the verdict UUID
     * @param verdictStatus   the verdict status literal (e.g., "RELIABLE", "AT_RISK")
     * @param createdAt       when the verdict was created
     * @param transientFailure true if the data indicates a transient failure (stale/unavailable)
     */
    public record SnapshotVerdictResult(
            UUID verdictId,
            String verdictStatus,
            OffsetDateTime createdAt,
            boolean transientFailure
    ) {}

    /**
     * Retrieve the SHA-256 audit hash for a given verdict ID.
     * Used by the notification module (via facade call) to include the audit proof hash
     * in email notifications (AC4 — email content includes SHA-256 hash).
     *
     * <p>This method does NOT require TenantContext because the event listener that calls it
     * runs in a background context without a user session. The verdict_id is globally unique.
     *
     * @param verdictId the verdict UUID
     * @return the SHA-256 hash string, or {@code null} if no audit log entry exists
     */
    public String getAuditHashByVerdictId(UUID verdictId) {
        if (verdictId == null) {
            return null;
        }
        return screeningRepository.findAuditHashByVerdictId(verdictId).orElse(null);
    }

    /**
     * Public-safe company data for the SEO gateway stub page.
     * Domain record returned by {@link #getPublicCompanyData} — mapped to
     * {@code PublicCompanyResponse} DTO by the controller layer.
     *
     * @param taxNumber   normalized tax number
     * @param companyName company display name (nullable)
     * @param address     company address (nullable)
     */
    public record PublicCompanyData(String taxNumber, String companyName, String address) {}

    /**
     * Retrieve public-safe company data for the SEO gateway stub page.
     * This method is intentionally unauthenticated and cross-tenant — it returns
     * only the company name, tax number, and address from the most recent snapshot.
     *
     * <p>NO verdict, NO audit hash, NO tenant data is included.
     *
     * @param taxNumber normalized tax number
     * @return public company data, or empty if no snapshot exists
     */
    public Optional<PublicCompanyData> getPublicCompanyData(String taxNumber) {
        String normalizedTaxNumber = taxNumber.replaceAll("[\\s-]", "");

        return screeningRepository.findMostRecentPublicSnapshot(normalizedTaxNumber)
                .map(snapshot -> {
                    Map<String, Object> jsonbMap = parseJsonbToMap(snapshot.snapshotData());
                    SnapshotData parsed = SnapshotDataParser.parse(jsonbMap);

                    // Extract address from raw JSONB — not in SnapshotData domain model
                    String address = extractAddress(jsonbMap);

                    return new PublicCompanyData(
                            normalizedTaxNumber,
                            parsed.companyName(),
                            address
                    );
                });
    }

    /**
     * Extract address from raw snapshot JSONB by checking each adapter's data.
     * Returns the first non-blank address found, or null if none available.
     *
     * <p>Uses safe instanceof checks to avoid ClassCastException on malformed JSONB
     * with non-String keys (review finding: unsafe cast on public unauthenticated endpoint).
     */
    private String extractAddress(Map<String, Object> jsonbMap) {
        if (jsonbMap == null || jsonbMap.isEmpty()) {
            return null;
        }
        for (Object value : jsonbMap.values()) {
            if (value instanceof Map<?, ?> adapterData) {
                Object address = adapterData.get("address");
                if (address instanceof String addr && !addr.isBlank()) {
                    return addr;
                }
            }
        }
        return null;
    }

    /**
     * Canonical public URLs for known government data source adapters.
     * Used to populate the "View source" link in the Provenance Sidebar.
     * Adapter names match the keys used in the snapshot JSONB (e.g. "nav-debt", "e-cegjegyzek").
     */
    private static final Map<String, String> KNOWN_SOURCE_URLS = Map.of(
            "nav-debt",          "https://nav.gov.hu/ellenorzesi-adatbazisok/nav-online-adatbazis",
            "e-cegjegyzek",      "https://e-cegjegyzek.hu",
            "cegkozlony",        "https://cegkozlony.hu",
            "nav-online-szamla", "https://onlineszamla.nav.gov.hu"
    );

    /**
     * Retrieve provenance data for a snapshot — the per-source availability details
     * used by the Provenance Sidebar in the Verdict Detail page (Story 2.4).
     *
     * <p>Tenant isolation is enforced by {@code ScreeningRepository.findSnapshotById},
     * which scopes the query via {@code TenantContext} (set by {@code TenantFilter}).
     * No explicit tenantId parameter is needed here.
     *
     * @param snapshotId the snapshot UUID to retrieve provenance for
     * @return populated ProvenanceResponse, or empty if snapshot not found / not owned by tenant
     */
    public Optional<ProvenanceResponse> getSnapshotProvenance(UUID snapshotId) {
        return screeningRepository.findSnapshotById(snapshotId)
                .map(snapshot -> {
                    // Parse raw JSONB into typed SnapshotData to extract source availability
                    Map<String, Object> jsonbMap = parseJsonbToMap(snapshot.snapshotData());
                    SnapshotData parsed = SnapshotDataParser.parse(jsonbMap);

                    // Map adapter names to canonical source URLs for the "View source" links.
                    // Demo adapters ("demo", "demo-*") are internal and have no public URL.
                    return ProvenanceResponse.from(snapshot.id(), snapshot.taxNumber(),
                            snapshot.checkedAt(), parsed, KNOWN_SOURCE_URLS);
                });
    }

    /**
     * Parse a JSONB value into a Map for use with {@link SnapshotDataParser}.
     * Returns an empty map if the JSONB is null or empty.
     */
    private static final ObjectMapper JSONB_MAPPER = new ObjectMapper();

    private Map<String, Object> parseJsonbToMap(org.jooq.JSONB jsonb) {
        if (jsonb == null || jsonb.data() == null || jsonb.data().isBlank() || "{}".equals(jsonb.data())) {
            return Map.of();
        }
        try {
            return JSONB_MAPPER.readValue(jsonb.data(), new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse snapshot JSONB for provenance: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Check if a snapshot exists for a given tenant and tax number.
     * Used by the guest search flow to determine if a tax number has been searched before
     * in a guest session (the guest's synthetic tenant ID scopes the query).
     *
     * @param tenantId  the tenant ID (synthetic for guests)
     * @param taxNumber normalized tax number
     * @return true if a snapshot exists for this tenant + tax number
     */
    public boolean hasSnapshotForTenant(UUID tenantId, String taxNumber) {
        return screeningRepository.existsSnapshotByTenantAndTaxNumber(tenantId, taxNumber);
    }

    // ─── Audit History Facade (Story 5.1a) ──────────────────────────────────

    /**
     * Write an AUTOMATED audit log entry after a successful live ingestor refresh.
     * Sets TenantContext to the partner's tenant for the duration of the write, then clears it.
     *
     * <p><b>Do NOT call {@link TenantContext#setCurrentTenant} before calling this method</b> —
     * this method manages TenantContext internally. If TenantContext is already set to the
     * correct tenant, pass it explicitly and this method will set it again (idempotent).
     *
     * <p>If the snapshotId cannot be loaded or verdict re-evaluation fails, the error is logged
     * and no audit record is written (the existing snapshot is unaffected).
     *
     * @param taxNumber  normalized tax number
     * @param userId     watchlist entry owner (from tenant_mandates); null = skip audit write
     * @param tenantId   tenant that owns the watchlist entry
     * @param snapshotId the snapshot that was just refreshed
     * @param mode       data source mode ("live" or "test")
     */
    public void auditIngestorRefresh(String taxNumber, UUID userId, UUID tenantId,
                                     UUID snapshotId, String mode) {
        if (userId == null) {
            log.debug("auditIngestorRefresh: no userId for tenant={} — skipping audit write", tenantId);
            return;
        }
        try {
            TenantContext.setCurrentTenant(tenantId);

            // Load fresh snapshot data via existing findSnapshotById (tenant-scoped)
            SnapshotRecord snapshot = screeningRepository.findSnapshotById(snapshotId).orElse(null);
            if (snapshot == null) {
                log.warn("auditIngestorRefresh: snapshot not found snapshotId={} tenant={}", snapshotId, tenantId);
                return;
            }

            // Parse snapshot and re-evaluate verdict
            Map<String, Object> jsonbMap = parseJsonbToMap(snapshot.snapshotData());
            SnapshotData parsedData = SnapshotDataParser.parse(jsonbMap);
            OffsetDateTime now = OffsetDateTime.now();
            VerdictResult verdictResult = VerdictEngine.evaluate(parsedData, now, freshnessConfig, now);

            // Serialize snapshot data JSON for hash
            String snapshotDataJson;
            try {
                snapshotDataJson = JSONB_MAPPER.writeValueAsString(jsonbMap);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.warn("auditIngestorRefresh: failed to serialize snapshot for hash — will use HASH_UNAVAILABLE");
                snapshotDataJson = null;
            }

            // Look up the existing verdict for this snapshot — do NOT create a new one
            // (creating a new verdict row per ingestor run causes data bloat and duplicate rows).
            UUID verdictId = screeningRepository.findLatestVerdictIdForSnapshot(snapshotId).orElse(null);
            screeningRepository.writeAuditLog(
                    taxNumber, userId, disclaimerText,
                    snapshotDataJson,
                    verdictResult.status().getLiteral(),
                    verdictResult.confidence().getLiteral(),
                    verdictId,
                    now,
                    "AUTOMATED",
                    mode.toUpperCase());

        } catch (Exception e) {
            log.error("auditIngestorRefresh failed for tenant={}", tenantId, e);
        }
        // Note: TenantContext is managed by the ingestor loop — do NOT clear it here
    }

    /**
     * Retrieve a paginated, filtered audit history for the current tenant.
     * TenantContext must be set by the caller (controller layer via TenantFilter).
     *
     * @param filter  filter parameters (all fields nullable)
     * @param page    zero-based page number
     * @param size    page size (must be positive)
     * @return page result with entries and total count
     */
    public AuditHistoryPage getAuditHistory(AuditHistoryFilter filter, int page, int size) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("CRITICAL: Missing tenant context for audit history operation");
        }
        long offset = (long) page * size;
        List<AuditHistoryRow> rows = screeningRepository.findAuditHistoryPage(tenantId, filter, offset, size);
        long total = screeningRepository.countAuditHistory(tenantId, filter);

        List<AuditHistoryEntry> entries = new ArrayList<>(rows.size());
        for (AuditHistoryRow row : rows) {
            List<String> sourceUrls = parseSourceUrls(row.sourceUrlsJson());
            entries.add(new AuditHistoryEntry(
                    row.id(), row.companyName(), row.taxNumber(),
                    row.verdictStatus(), row.verdictConfidence(),
                    row.searchedAt(), row.sha256Hash(),
                    row.dataSourceMode(), row.checkSource(),
                    sourceUrls, row.disclaimerText()));
        }
        return new AuditHistoryPage(entries, total, page, size);
    }

    /**
     * Verify the SHA-256 hash for a given audit entry by re-computing from stored inputs.
     * Returns the match result including both computed and stored hash for display.
     *
     * @param auditId  the audit log row UUID
     * @return verify result, or empty if audit entry not found / not accessible to this tenant
     */
    public Optional<AuditHashVerifyResult> verifyAuditHash(UUID auditId) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("CRITICAL: Missing tenant context for audit hash verify");
        }
        return screeningRepository.findAuditEntryForVerification(auditId, tenantId)
                .map(row -> {
                    String computed;
                    try {
                        computed = HashUtil.sha256(
                                row.snapshotDataJson(),
                                row.verdictStatus(),
                                row.verdictConfidence(),
                                row.disclaimerText());
                    } catch (IllegalArgumentException e) {
                        computed = ScreeningRepository.HASH_UNAVAILABLE_SENTINEL;
                    }
                    // Sentinel in either hash → original was never computed or current re-computation failed.
                    // Mismatch is treated as unavailable: can't distinguish tampering from a legitimate
                    // ingestor refresh that updated the snapshot data (stale-snapshot limitation, option 2).
                    boolean sentinelStored = ScreeningRepository.HASH_UNAVAILABLE_SENTINEL.equals(row.storedHash());
                    boolean sentinelComputed = ScreeningRepository.HASH_UNAVAILABLE_SENTINEL.equals(computed);
                    boolean match = !sentinelStored && !sentinelComputed && computed.equals(row.storedHash());
                    boolean unavailable = sentinelStored || sentinelComputed || !match;
                    return new AuditHashVerifyResult(match, computed, row.storedHash(), unavailable);
                });
    }

    /**
     * Page result for audit history queries.
     *
     * @param entries       entries on this page
     * @param totalElements total number of matching rows (across all pages)
     * @param page          zero-based page number
     * @param size          page size requested
     */
    public record AuditHistoryPage(
            List<AuditHistoryEntry> entries,
            long totalElements,
            int page,
            int size
    ) {}

    private List<String> parseSourceUrls(String sourceUrlsJson) {
        if (sourceUrlsJson == null || sourceUrlsJson.isBlank() || "[]".equals(sourceUrlsJson)) {
            return List.of();
        }
        try {
            return JSONB_MAPPER.readValue(sourceUrlsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.debug("Failed to parse source_urls JSON: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Domain result of a partner search.
     * Mapped to VerdictResponse by the controller layer.
     * Uses real enum types from the VerdictEngine state machine (Story 2.3).
     *
     * @param riskSignals list of reason codes explaining the verdict (e.g., "PUBLIC_DEBT_DETECTED").
     *                    May be empty for cached results even on non-RELIABLE verdicts.
     * @param cached      true if this result was served from the idempotency cache (riskSignals
     *                    may be empty). false if freshly computed by VerdictEngine.
     * @param companyName company display name extracted from snapshot JSONB (first available adapter);
     *                    null for cached results or if no adapter returned a company name.
     * @param sha256Hash  SHA-256 audit hash from search_audit_log for legal proof display;
     *                    null for cached results (audit log was written on the original search).
     */
    public record SearchResult(
            UUID verdictId,
            UUID snapshotId,
            String taxNumber,
            VerdictStatus status,
            VerdictConfidence confidence,
            OffsetDateTime createdAt,
            List<String> riskSignals,
            boolean cached,
            String companyName,
            String sha256Hash
    ) {}
}
