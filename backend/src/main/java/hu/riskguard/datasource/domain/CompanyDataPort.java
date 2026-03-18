package hu.riskguard.datasource.domain;

import hu.riskguard.datasource.api.dto.ScrapedData;

import java.util.Set;

/**
 * Port interface for data source adapters.
 * Each adapter implementation fetches company data from a specific Hungarian government data source.
 *
 * <p>Implementations may be annotated with Resilience4j {@code @CircuitBreaker} and {@code @Retry}
 * for fault tolerance when calling external APIs. The {@link hu.riskguard.datasource.internal.CompanyDataAggregator}
 * orchestrates parallel execution of all registered ports using virtual threads.
 */
public interface CompanyDataPort {

    /**
     * Fetch company data for the given tax number from this adapter's data source.
     *
     * @param taxNumber the normalized Hungarian tax number (8 or 11 digits, no hyphens)
     * @return data with availability status; never null
     */
    ScrapedData fetch(String taxNumber);

    /**
     * @return the canonical adapter name (e.g., "demo", "nav-online-szamla")
     */
    String adapterName();

    /**
     * @return set of required data field names this adapter is expected to provide
     */
    Set<String> requiredFields();
}
