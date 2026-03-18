package hu.riskguard.datasource.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Aggregated data result from all adapters. Returned by {@code DataSourceService.fetchCompanyData()}.
 *
 * @param snapshotData      consolidated JSONB-ready map of all data keyed by adapter name
 * @param sourceUrls        combined list of all source URLs accessed across adapters
 * @param adapterResults    per-adapter availability status (adapter name → ScrapedData)
 * @param domFingerprintHash SHA-256 hash of concatenated raw data for change detection; may be null
 */
public record CompanyData(
        Map<String, Object> snapshotData,
        List<String> sourceUrls,
        Map<String, ScrapedData> adapterResults,
        String domFingerprintHash
) {}
