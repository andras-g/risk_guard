package hu.riskguard.datasource.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Per-adapter data result. Immutable record returned by each {@code CompanyDataPort} implementation.
 *
 * @param adapterName  canonical adapter name (e.g., "demo")
 * @param data         extracted key-value data from the source; empty map on failure
 * @param sourceUrls   list of URLs accessed during data retrieval (provenance tracking)
 * @param available    true if the adapter returned data successfully; false if unavailable
 * @param errorReason  human-readable error description when unavailable; null on success
 */
public record ScrapedData(
        String adapterName,
        Map<String, Object> data,
        List<String> sourceUrls,
        boolean available,
        String errorReason
) {}
