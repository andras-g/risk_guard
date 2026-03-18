package hu.riskguard.datasource.internal;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.security.TenantContext;
import hu.riskguard.core.util.HashUtil;
import hu.riskguard.datasource.api.dto.CompanyData;
import hu.riskguard.datasource.api.dto.ScrapedData;
import hu.riskguard.datasource.domain.CompanyDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

/**
 * Orchestrates parallel data retrieval from all registered {@link CompanyDataPort} adapters
 * using Java 25 {@link StructuredTaskScope} with virtual threads.
 *
 * <p>Uses {@code Joiner.awaitAll()} to wait for ALL tasks to complete (success or failure).
 * This ensures partial results are collected — if some adapters fail, successful results
 * are still returned with failed sources marked as {@code SOURCE_UNAVAILABLE}.
 *
 * <p>Tenant context is manually propagated from the parent thread into each forked virtual thread
 * since {@code TenantContext} uses {@code ThreadLocal} which is not inherited by virtual threads.
 *
 * // TODO: migrate TenantContext to ScopedValue when core refactored
 */
@Component
public class CompanyDataAggregator {

    private static final Logger log = LoggerFactory.getLogger(CompanyDataAggregator.class);

    private final List<CompanyDataPort> adapters;
    private final RiskGuardProperties properties;

    public CompanyDataAggregator(List<CompanyDataPort> adapters, RiskGuardProperties properties) {
        this.adapters = adapters;
        this.properties = properties;
    }

    /**
     * Aggregate company data from all registered adapters in parallel.
     * Uses StructuredTaskScope with virtual threads, Joiner.awaitAll(), and a hard timeout ceiling.
     *
     * @param taxNumber normalized Hungarian tax number
     * @return aggregated CompanyData with per-source availability
     */
    public CompanyData aggregate(String taxNumber) {
        UUID tenantId = TenantContext.getCurrentTenant(); // capture BEFORE fork
        int globalDeadlineSeconds = properties.getDataSource().getGlobalDeadlineSeconds();

        log.info("Starting parallel data retrieval tax_number={} adapter_count={} deadline_seconds={}",
                maskTaxNumber(taxNumber), adapters.size(), globalDeadlineSeconds);

        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<ScrapedData>awaitAll(),
                cfg -> cfg.withTimeout(Duration.ofSeconds(globalDeadlineSeconds)))) {

            // Fork one virtual thread per adapter with tenant propagation
            Map<String, Subtask<ScrapedData>> subtasks = new LinkedHashMap<>();
            for (CompanyDataPort adapter : adapters) {
                Subtask<ScrapedData> subtask = scope.fork(
                        () -> withTenant(tenantId, () -> adapter.fetch(taxNumber)));
                subtasks.put(adapter.adapterName(), subtask);
            }

            // Wait for all tasks (or timeout)
            try {
                scope.join();
            } catch (StructuredTaskScope.TimeoutException e) {
                // Timeout expired — collect whatever partial results are available
                log.warn("Global data retrieval deadline exceeded tax_number={} deadline_seconds={}",
                        maskTaxNumber(taxNumber), globalDeadlineSeconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Data retrieval interrupted tax_number={}", maskTaxNumber(taxNumber));
                // Return all-unavailable result — subtasks may not have completed their join(),
                // so accessing their state is unsafe.
                return allUnavailableResult(subtasks.keySet(), taxNumber);
            }

            // Collect results — check each subtask state individually
            return mergeResults(subtasks, taxNumber);
        }
    }

    private CompanyData mergeResults(
            Map<String, Subtask<ScrapedData>> subtasks,
            String taxNumber) {

        Map<String, Object> snapshotData = new LinkedHashMap<>();
        List<String> allSourceUrls = new ArrayList<>();
        Map<String, ScrapedData> adapterResults = new LinkedHashMap<>();
        StringBuilder domFingerprint = new StringBuilder();

        for (var entry : subtasks.entrySet()) {
            String adapterName = entry.getKey();
            var subtask = entry.getValue();

            try {
                switch (subtask.state()) {
                    case SUCCESS -> {
                        ScrapedData result = subtask.get();
                        if (result.available()) {
                            snapshotData.put(adapterName, result.data());
                            allSourceUrls.addAll(result.sourceUrls());
                            domFingerprint.append(result.data().toString());
                        } else {
                            // Preserve partial data from degraded sources — "positive evidence is
                            // actionable even from degraded sources" (architecture design principle).
                            // Merge status/reason metadata into the adapter's actual data rather than
                            // discarding it, so risk-relevant fields (e.g., hasPublicDebt) survive.
                            Map<String, Object> mergedData = new LinkedHashMap<>(result.data());
                            mergedData.put("status", "SOURCE_UNAVAILABLE");
                            mergedData.put("reason", result.errorReason() != null ? result.errorReason() : "unknown");
                            snapshotData.put(adapterName, Map.copyOf(mergedData));
                        }
                        adapterResults.put(adapterName, result);
                        log.info("Subtask result adapter_name={} tax_number={} state=SUCCESS available={}",
                                adapterName, maskTaxNumber(taxNumber), result.available());
                    }
                    case FAILED -> {
                        ScrapedData unavailable = new ScrapedData(adapterName, Map.of(), List.of(), false,
                                "FAILED: " + subtask.exception().getMessage());
                        snapshotData.put(adapterName, Map.of("status", "SOURCE_UNAVAILABLE",
                                "reason", subtask.exception().getMessage()));
                        adapterResults.put(adapterName, unavailable);
                        log.warn("Subtask failed adapter_name={} tax_number={} state=FAILED error={}",
                                adapterName, maskTaxNumber(taxNumber), subtask.exception().getMessage());
                    }
                    case UNAVAILABLE -> {
                        ScrapedData unavailable = new ScrapedData(adapterName, Map.of(), List.of(), false,
                                "TIMEOUT: task did not complete within deadline");
                        snapshotData.put(adapterName, Map.of("status", "SOURCE_UNAVAILABLE",
                                "reason", "timeout"));
                        adapterResults.put(adapterName, unavailable);
                        log.warn("Subtask timed out adapter_name={} tax_number={} state=UNAVAILABLE",
                                adapterName, maskTaxNumber(taxNumber));
                    }
                }
            } catch (Exception e) {
                ScrapedData unavailable = new ScrapedData(adapterName, Map.of(), List.of(), false,
                        "ERROR: " + e.getMessage());
                snapshotData.put(adapterName, Map.of("status", "SOURCE_UNAVAILABLE",
                        "reason", e.getMessage()));
                adapterResults.put(adapterName, unavailable);
                log.error("Unexpected error processing subtask adapter_name={} tax_number={}",
                        adapterName, maskTaxNumber(taxNumber), e);
            }
        }

        // TODO: DOM fingerprint should ideally hash raw HTML response bodies for true change detection.
        // Currently hashes parsed data map toString(), which changes when parsing logic changes
        // even if the source HTML hasn't changed. Requires carrying raw HTML in ScrapedData (Story 6.1).
        String hash = domFingerprint.isEmpty() ? null : HashUtil.sha256(domFingerprint.toString());
        return new CompanyData(snapshotData, allSourceUrls, adapterResults, hash);
    }

    /**
     * Tenant propagation helper — sets TenantContext inside the virtual thread
     * and clears it in a finally block to prevent leaks.
     */
    private <T> T withTenant(UUID tenantId, Callable<T> task) throws Exception {
        if (tenantId != null) {
            TenantContext.setCurrentTenant(tenantId);
        }
        try {
            return task.call();
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Build an all-unavailable result when the scope was interrupted before join() completed.
     * Accessing subtask state is unsafe in this case, so we mark all adapters as SOURCE_UNAVAILABLE.
     */
    private CompanyData allUnavailableResult(Set<String> adapterNames, String taxNumber) {
        Map<String, Object> snapshotData = new LinkedHashMap<>();
        Map<String, ScrapedData> adapterResults = new LinkedHashMap<>();
        for (String name : adapterNames) {
            ScrapedData unavailable = new ScrapedData(name, Map.of(), List.of(), false,
                    "INTERRUPTED: data retrieval thread was interrupted");
            snapshotData.put(name, Map.of("status", "SOURCE_UNAVAILABLE", "reason", "interrupted"));
            adapterResults.put(name, unavailable);
            log.warn("Marking adapter unavailable due to interruption adapter_name={} tax_number={}",
                    name, maskTaxNumber(taxNumber));
        }
        return new CompanyData(snapshotData, List.of(), adapterResults, null);
    }

    private static String maskTaxNumber(String taxNumber) {
        return DataSourceLoggingUtil.maskTaxNumber(taxNumber);
    }
}
