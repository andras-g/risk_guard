package hu.riskguard.datasource.domain;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.datasource.api.dto.CompanyData;
import hu.riskguard.datasource.internal.CompanyDataAggregator;
import hu.riskguard.datasource.internal.NavTenantCredentialRepository;
import hu.riskguard.datasource.internal.adapters.demo.DemoInvoiceFixtures;
import hu.riskguard.datasource.internal.adapters.nav.NavOnlineSzamlaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Module facade for the data source module.
 * This is the ONLY public entry point into the data source module's business logic.
 *
 * <p>External modules call {@code fetchCompanyData()} to trigger parallel data retrieval
 * from all registered adapters. Results are returned as a {@link CompanyData}
 * record — the caller (ScreeningService) is responsible for persisting the data.
 *
 * <p>Also provides invoice query methods for EPR auto-fill: {@code queryInvoices()} and
 * {@code queryInvoiceDetails()}. In demo mode these serve from {@link DemoInvoiceFixtures};
 * in non-demo mode they delegate to {@link NavOnlineSzamlaClient}.
 */
@Service
public class DataSourceService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceService.class);

    private final CompanyDataAggregator aggregator;
    private final NavOnlineSzamlaClient navClient;
    private final RiskGuardProperties properties;
    private final NavTenantCredentialRepository credentialRepository;

    public DataSourceService(CompanyDataAggregator aggregator,
                             NavOnlineSzamlaClient navClient,
                             RiskGuardProperties properties,
                             NavTenantCredentialRepository credentialRepository) {
        this.aggregator = aggregator;
        this.navClient = navClient;
        this.properties = properties;
        this.credentialRepository = credentialRepository;
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

    /**
     * Query invoice summaries for a given company within a date range.
     *
     * <p>Demo mode: serves from {@link DemoInvoiceFixtures}, filtered by date and direction.
     * Non-demo mode: delegates to NAV Online Számla {@code queryInvoiceDigest}.
     * On any exception: logs warning and returns empty list (NAV downtime must not crash EPR page).
     *
     * @param taxNumber 8-digit Hungarian tax number
     * @param from      start of issue date range (inclusive)
     * @param to        end of issue date range (inclusive)
     * @param direction OUTBOUND (sales) or INBOUND (purchases)
     * @return list of invoice summaries, empty on error
     */
    public InvoiceQueryResult queryInvoices(String taxNumber, LocalDate from, LocalDate to, InvoiceDirection direction) {
        try {
            List<InvoiceSummary> summaries;
            if (isDemo()) {
                summaries = DemoInvoiceFixtures.getForTaxNumber(taxNumber).stream()
                        .filter(f -> !f.issueDate().isBefore(from) && !f.issueDate().isAfter(to))
                        .filter(f -> direction.name().equals(f.direction()))
                        .map(this::toInvoiceSummary)
                        .toList();
            } else {
                hu.riskguard.datasource.internal.adapters.nav.InvoiceDirection navDir =
                        direction == InvoiceDirection.OUTBOUND
                                ? hu.riskguard.datasource.internal.adapters.nav.InvoiceDirection.OUTBOUND
                                : hu.riskguard.datasource.internal.adapters.nav.InvoiceDirection.INBOUND;
                summaries = navClient.queryInvoiceDigest(taxNumber, from, to, navDir).stream()
                        .map(this::fromNavSummary)
                        .toList();
            }
            return new InvoiceQueryResult(summaries, true);
        } catch (Exception e) {
            log.warn("queryInvoices failed for taxNumber={}: {}", maskTaxNumber(taxNumber), e.getMessage());
            return new InvoiceQueryResult(List.of(), false);
        }
    }

    /**
     * Fetch full invoice details including line items.
     *
     * <p>Demo mode: returns matching {@link DemoInvoiceFixtures} entry mapped to {@link InvoiceDetail}.
     * Non-demo mode: delegates to NAV Online Számla {@code queryInvoiceData}.
     * On any exception: returns {@link InvoiceDetail} with empty line items.
     *
     * @param invoiceNumber unique invoice identifier
     * @return full invoice detail with line items; line items empty on error
     */
    public InvoiceDetail queryInvoiceDetails(String invoiceNumber) {
        try {
            if (isDemo()) {
                return DemoInvoiceFixtures.getAllFixtures().stream()
                        .filter(f -> invoiceNumber.equals(f.invoiceNumber()))
                        .findFirst()
                        .map(this::toInvoiceDetail)
                        .orElse(emptyDetail(invoiceNumber));
            } else {
                return fromNavDetail(navClient.queryInvoiceData(invoiceNumber));
            }
        } catch (Exception e) {
            log.warn("queryInvoiceDetails failed for invoiceNumber={}: {}", invoiceNumber, e.getMessage());
            return emptyDetail(invoiceNumber);
        }
    }

    /**
     * Returns the current data source mode string (e.g., "demo", "test", "live").
     */
    public String getMode() {
        return properties.getDataSource().getMode();
    }

    /**
     * Returns the tax number registered for the given tenant in NAV credentials, if any.
     * Used for ownership validation — ensures a tenant can only query invoices for their own company.
     *
     * @param tenantId the tenant UUID
     * @return the registered tax number, or empty if no NAV credentials configured
     */
    public Optional<String> getTenantTaxNumber(UUID tenantId) {
        return credentialRepository.findByTenantId(tenantId)
                .map(NavTenantCredentialRepository.CredentialRow::taxNumber);
    }

    // ─── Private helpers ────────────────────────────────────────────────────────

    private boolean isDemo() {
        return "demo".equals(properties.getDataSource().getMode());
    }

    private InvoiceSummary toInvoiceSummary(DemoInvoiceFixtures.InvoiceFixture fixture) {
        InvoiceDirection dir = "OUTBOUND".equals(fixture.direction())
                ? InvoiceDirection.OUTBOUND : InvoiceDirection.INBOUND;
        return new InvoiceSummary(
                fixture.invoiceNumber(),
                "CREATE",
                fixture.supplierTaxNumber(),
                null,
                fixture.customerTaxNumber(),
                null,
                fixture.issueDate(),
                null,
                BigDecimal.ZERO,
                "HUF",
                dir
        );
    }

    private InvoiceDetail toInvoiceDetail(DemoInvoiceFixtures.InvoiceFixture fixture) {
        InvoiceDirection dir = "OUTBOUND".equals(fixture.direction())
                ? InvoiceDirection.OUTBOUND : InvoiceDirection.INBOUND;
        List<InvoiceLineItem> lineItems = fixture.lineItems().stream()
                .map(li -> new InvoiceLineItem(
                        li.lineNumber(),
                        li.description(),
                        li.quantity(),
                        li.unitOfMeasure(),
                        li.unitPrice(),
                        li.netAmount(),
                        li.netAmount(),
                        li.vtszCode(),
                        "VTSZ",
                        li.vtszCode()
                ))
                .toList();
        return new InvoiceDetail(
                fixture.invoiceNumber(),
                "CREATE",
                fixture.supplierTaxNumber(),
                null,
                fixture.customerTaxNumber(),
                null,
                fixture.issueDate(),
                null,
                BigDecimal.ZERO,
                "HUF",
                dir,
                lineItems,
                null,
                Map.of()
        );
    }

    private InvoiceSummary fromNavSummary(hu.riskguard.datasource.internal.adapters.nav.InvoiceSummary nav) {
        InvoiceDirection dir = nav.invoiceDirection() == hu.riskguard.datasource.internal.adapters.nav.InvoiceDirection.OUTBOUND
                ? InvoiceDirection.OUTBOUND : InvoiceDirection.INBOUND;
        return new InvoiceSummary(
                nav.invoiceNumber(),
                nav.invoiceOperation(),
                nav.supplierTaxNumber(),
                nav.supplierName(),
                nav.customerTaxNumber(),
                nav.customerName(),
                nav.invoiceIssueDate(),
                nav.invoiceDeliveryDate(),
                nav.invoiceNetAmount(),
                nav.invoiceCurrency(),
                dir
        );
    }

    private InvoiceDetail fromNavDetail(hu.riskguard.datasource.internal.adapters.nav.InvoiceDetail nav) {
        InvoiceDirection dir = nav.invoiceDirection() == hu.riskguard.datasource.internal.adapters.nav.InvoiceDirection.OUTBOUND
                ? InvoiceDirection.OUTBOUND : InvoiceDirection.INBOUND;
        List<InvoiceLineItem> lineItems = nav.lineItems().stream()
                .map(li -> new InvoiceLineItem(
                        li.lineNumber(),
                        li.lineDescription(),
                        li.quantity(),
                        li.unitOfMeasure(),
                        li.unitPrice(),
                        li.lineNetAmount(),
                        li.lineNetAmountHUF(),
                        li.vtszCode(),
                        li.productCodeCategory(),
                        li.productCodeValue()
                ))
                .toList();
        return new InvoiceDetail(
                nav.invoiceNumber(),
                nav.invoiceOperation(),
                nav.supplierTaxNumber(),
                nav.supplierName(),
                nav.customerTaxNumber(),
                nav.customerName(),
                nav.invoiceIssueDate(),
                nav.invoiceDeliveryDate(),
                nav.invoiceNetAmount(),
                nav.invoiceCurrency(),
                dir,
                lineItems,
                nav.paymentMethod(),
                nav.vatRateSummary()
        );
    }

    private InvoiceDetail emptyDetail(String invoiceNumber) {
        return new InvoiceDetail(invoiceNumber, null, null, null, null, null,
                null, null, BigDecimal.ZERO, "HUF", InvoiceDirection.OUTBOUND,
                List.of(), null, Map.of());
    }

    private static String maskTaxNumber(String taxNumber) {
        if (taxNumber == null || taxNumber.length() < 4) return "***";
        return taxNumber.substring(0, 4) + "****";
    }
}
