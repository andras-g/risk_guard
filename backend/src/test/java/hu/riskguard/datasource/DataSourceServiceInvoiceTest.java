package hu.riskguard.datasource;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.datasource.domain.InvoiceDirection;
import hu.riskguard.datasource.domain.InvoiceQueryResult;
import hu.riskguard.datasource.domain.InvoiceSummary;
import hu.riskguard.datasource.domain.InvoiceDetail;
import hu.riskguard.datasource.internal.CompanyDataAggregator;
import hu.riskguard.datasource.internal.NavTenantCredentialRepository;
import hu.riskguard.datasource.internal.adapters.nav.NavOnlineSzamlaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for invoice query methods on {@link DataSourceService}.
 */
@ExtendWith(MockitoExtension.class)
class DataSourceServiceInvoiceTest {

    @Mock
    private CompanyDataAggregator aggregator;

    @Mock
    private NavOnlineSzamlaClient navClient;

    @Mock
    private NavTenantCredentialRepository credentialRepository;

    private DataSourceService service;

    private static final String TAX_NUMBER = "12345678";
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    @BeforeEach
    void setUp() {
        RiskGuardProperties props = new RiskGuardProperties();
        props.getDataSource().setMode("demo");
        service = new DataSourceService(aggregator, navClient, props, credentialRepository);
    }

    @Test
    void queryInvoices_demoMode_returnsFixturesForTradeCompany() {
        InvoiceQueryResult result = service.queryInvoices(TAX_NUMBER, FROM, TO, InvoiceDirection.OUTBOUND);

        assertThat(result.summaries()).isNotEmpty();
        assertThat(result.serviceAvailable()).isTrue();
        assertThat(result.summaries()).allMatch(s -> s.invoiceDirection() == InvoiceDirection.OUTBOUND);
    }

    @Test
    void queryInvoices_demoMode_dateFilterApplied() {
        // Only Q1 2026 data present in demo fixtures
        LocalDate strictFrom = LocalDate.of(2026, 2, 1);
        LocalDate strictTo = LocalDate.of(2026, 2, 28);

        InvoiceQueryResult all = service.queryInvoices(TAX_NUMBER, FROM, TO, InvoiceDirection.OUTBOUND);
        InvoiceQueryResult filtered = service.queryInvoices(TAX_NUMBER, strictFrom, strictTo, InvoiceDirection.OUTBOUND);

        assertThat(filtered.summaries().size()).isLessThanOrEqualTo(all.summaries().size());
        assertThat(filtered.summaries()).allMatch(s ->
                !s.invoiceIssueDate().isBefore(strictFrom) && !s.invoiceIssueDate().isAfter(strictTo));
    }

    @Test
    void queryInvoices_demoMode_directionFilterApplied() {
        InvoiceQueryResult outbound = service.queryInvoices(TAX_NUMBER, FROM, TO, InvoiceDirection.OUTBOUND);
        InvoiceQueryResult inbound = service.queryInvoices(TAX_NUMBER, FROM, TO, InvoiceDirection.INBOUND);

        assertThat(outbound.summaries()).allMatch(s -> s.invoiceDirection() == InvoiceDirection.OUTBOUND);
        assertThat(inbound.summaries()).allMatch(s -> s.invoiceDirection() == InvoiceDirection.INBOUND);
    }

    @Test
    void queryInvoices_nonDemoMode_delegatesToNavClient() {
        RiskGuardProperties props = new RiskGuardProperties();
        props.getDataSource().setMode("live");
        service = new DataSourceService(aggregator, navClient, props, credentialRepository);

        when(navClient.queryInvoiceDigest(eq(TAX_NUMBER), eq(FROM), eq(TO),
                eq(hu.riskguard.datasource.internal.adapters.nav.InvoiceDirection.OUTBOUND)))
                .thenReturn(List.of());

        InvoiceQueryResult result = service.queryInvoices(TAX_NUMBER, FROM, TO, InvoiceDirection.OUTBOUND);

        assertThat(result.summaries()).isEmpty();
        assertThat(result.serviceAvailable()).isTrue();
        verify(navClient).queryInvoiceDigest(TAX_NUMBER, FROM, TO,
                hu.riskguard.datasource.internal.adapters.nav.InvoiceDirection.OUTBOUND);
    }

    @Test
    void queryInvoices_demoMode_returnsEmptyForUnknownTaxNumber() {
        InvoiceQueryResult result = service.queryInvoices("00000000", FROM, TO, InvoiceDirection.OUTBOUND);
        assertThat(result.summaries()).isEmpty();
        assertThat(result.serviceAvailable()).isTrue();
    }

    @Test
    void queryInvoiceDetails_demoMode_returnsDetailWithLineItems() {
        // First get a valid invoice number from demo fixtures
        InvoiceQueryResult result = service.queryInvoices(TAX_NUMBER, FROM, TO, InvoiceDirection.OUTBOUND);
        assertThat(result.summaries()).isNotEmpty();

        String invoiceNumber = result.summaries().get(0).invoiceNumber();
        InvoiceDetail detail = service.queryInvoiceDetails(invoiceNumber);

        assertThat(detail).isNotNull();
        assertThat(detail.invoiceNumber()).isEqualTo(invoiceNumber);
        assertThat(detail.lineItems()).isNotEmpty();
    }

    @Test
    void queryInvoiceDetails_demoMode_returnsEmptyDetailForUnknown() {
        InvoiceDetail detail = service.queryInvoiceDetails("NONEXISTENT-999");
        assertThat(detail.invoiceNumber()).isEqualTo("NONEXISTENT-999");
        assertThat(detail.lineItems()).isEmpty();
    }

    @Test
    void queryInvoices_onNavException_returnsEmptyWithServiceUnavailable() {
        RiskGuardProperties props = new RiskGuardProperties();
        props.getDataSource().setMode("live");
        service = new DataSourceService(aggregator, navClient, props, credentialRepository);

        when(navClient.queryInvoiceDigest(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("NAV timeout"));

        InvoiceQueryResult result = service.queryInvoices(TAX_NUMBER, FROM, TO, InvoiceDirection.OUTBOUND);
        assertThat(result.summaries()).isEmpty();
        assertThat(result.serviceAvailable()).isFalse();
    }
}
