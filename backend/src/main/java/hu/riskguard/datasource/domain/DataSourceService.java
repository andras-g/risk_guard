package hu.riskguard.datasource.domain;

import hu.riskguard.datasource.api.dto.CompanyData;
import hu.riskguard.datasource.internal.CompanyDataAggregator;
import org.springframework.stereotype.Service;

/**
 * Module facade for the data source module.
 * This is the ONLY public entry point into the data source module's business logic.
 *
 * <p>External modules call {@code fetchCompanyData()} to trigger parallel data retrieval
 * from all registered adapters. Results are returned as a {@link CompanyData}
 * record — the caller (ScreeningService) is responsible for persisting the data.
 */
@Service
public class DataSourceService {

    private final CompanyDataAggregator aggregator;

    public DataSourceService(CompanyDataAggregator aggregator) {
        this.aggregator = aggregator;
    }

    /**
     * Fetch company data from all registered data source adapters in parallel.
     *
     * @param taxNumber normalized Hungarian tax number
     * @return aggregated company data with per-source availability status
     */
    public CompanyData fetchCompanyData(String taxNumber) {
        return aggregator.aggregate(taxNumber);
    }
}
