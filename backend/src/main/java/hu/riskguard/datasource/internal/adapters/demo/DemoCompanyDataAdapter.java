package hu.riskguard.datasource.internal.adapters.demo;

import hu.riskguard.datasource.api.dto.ScrapedData;
import hu.riskguard.datasource.domain.CompanyDataPort;
import hu.riskguard.datasource.internal.DataSourceLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Demo adapter that returns realistic Hungarian company data from in-memory fixtures.
 * Registered as a bean ONLY when {@code riskguard.data-source.mode=demo} via
 * {@link hu.riskguard.datasource.internal.DataSourceModeConfig}.
 *
 * <p>This adapter never fails — demo data is always available. No Resilience4j annotations
 * needed since there are no network calls or failure modes.
 *
 * <p>For unknown tax numbers, returns a generic clean (debt-free, no insolvency) company
 * to ensure demo mode always produces a usable result.
 */
public class DemoCompanyDataAdapter implements CompanyDataPort {

    private static final Logger log = LoggerFactory.getLogger(DemoCompanyDataAdapter.class);
    private static final String ADAPTER_NAME = "demo";

    @Override
    public ScrapedData fetch(String taxNumber) {
        log.info("Demo adapter fetching data for tax_number={}", DataSourceLoggingUtil.maskTaxNumber(taxNumber));

        // Normalize: strip hyphens and take first 8 digits for fixture lookup
        String normalizedKey = taxNumber.replace("-", "");
        if (normalizedKey.length() > 8) {
            normalizedKey = normalizedKey.substring(0, 8);
        }

        Map<String, Object> fixtureData = DemoCompanyFixtures.getCompanyData(normalizedKey);

        return new ScrapedData(
                ADAPTER_NAME,
                fixtureData,
                List.of("demo://in-memory/" + DataSourceLoggingUtil.maskTaxNumber(taxNumber)),
                true, // Demo adapter is always available
                null  // No error
        );
    }

    @Override
    public String adapterName() {
        return ADAPTER_NAME;
    }

    @Override
    public Set<String> requiredFields() {
        return Set.of("available", "hasPublicDebt", "taxNumberStatus",
                "hasInsolvencyProceedings", "companyName", "registrationNumber");
    }
}
