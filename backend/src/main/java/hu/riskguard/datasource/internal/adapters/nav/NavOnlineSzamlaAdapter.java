package hu.riskguard.datasource.internal.adapters.nav;

import hu.riskguard.datasource.api.dto.ScrapedData;
import hu.riskguard.datasource.domain.CompanyDataPort;
import hu.riskguard.datasource.internal.DataSourceLoggingUtil;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link CompanyDataPort} implementation backed by the NAV Online Számla API v3.
 *
 * <p>Registered as a bean ONLY when {@code riskguard.data-source.mode=test} or {@code live}
 * via {@link hu.riskguard.datasource.internal.DataSourceModeConfig}.
 *
 * <p>Calls {@link NavOnlineSzamlaClient#queryTaxpayer(String)} and maps the response to
 * {@link ScrapedData}. Wraps the call with a Resilience4j circuit breaker ({@code "nav-online-szamla"}).
 * On circuit breaker open or exception the {@link #fetchFallback} method returns
 * {@code ScrapedData(available=false)}.
 */
public class NavOnlineSzamlaAdapter implements CompanyDataPort {

    private static final Logger log = LoggerFactory.getLogger(NavOnlineSzamlaAdapter.class);
    private static final String ADAPTER_NAME = "nav-online-szamla";

    private final NavOnlineSzamlaClient navClient;

    public NavOnlineSzamlaAdapter(NavOnlineSzamlaClient navClient) {
        this.navClient = navClient;
    }

    @Override
    @CircuitBreaker(name = ADAPTER_NAME, fallbackMethod = "fetchFallback")
    public ScrapedData fetch(String taxNumber) {
        log.info("NAV adapter fetching data for tax_number={}", DataSourceLoggingUtil.maskTaxNumber(taxNumber));
        String normalised = taxNumber.replace("-", "");
        if (normalised.length() > 8) {
            normalised = normalised.substring(0, 8);
        }

        TaxpayerInfo info = navClient.queryTaxpayer(normalised);

        String baseUrl = "https://api.onlineszamla.nav.gov.hu/invoiceService/v3";
        return new ScrapedData(
                ADAPTER_NAME,
                Map.of(
                        "companyName", info.companyName() != null ? info.companyName() : "",
                        "taxNumberStatus", "VALID",
                        "incorporationType", info.incorporationType() != null
                                ? info.incorporationType().name() : "ORGANIZATION",
                        "vatGroupMembership", info.vatGroupMembership()
                ),
                List.of(baseUrl + "/queryTaxpayer"),
                true,
                null
        );
    }

    public ScrapedData fetchFallback(String taxNumber, Throwable t) {
        log.warn("NAV adapter circuit breaker fallback for tax_number={}: {}",
                DataSourceLoggingUtil.maskTaxNumber(taxNumber), t.getMessage());
        return new ScrapedData(
                ADAPTER_NAME,
                Map.of("available", false),
                List.of(),
                false,
                "NAV adapter temporarily unavailable: " + t.getMessage()
        );
    }

    @Override
    public String adapterName() {
        return ADAPTER_NAME;
    }

    @Override
    public Set<String> requiredFields() {
        return Set.of("companyName", "taxNumberStatus", "incorporationType");
    }
}
