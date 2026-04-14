package hu.riskguard.datasource.internal.adapters.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DemoInvoiceFixtures} — verifies invoice data completeness
 * and structural correctness for each fixture company.
 */
class DemoInvoiceFixturesTest {

    @ParameterizedTest(name = "company {0} has {1}-{2} invoices")
    @DisplayName("each fixture company has 20-50 invoices per quarter")
    @CsvSource({
            "12345678, 20, 50",  // Példa Kereskedelmi Kft.
            "99887766, 20, 50",  // Megbízható Építő Zrt.
            "11223344, 20, 50",  // Adós Szolgáltató Bt.
            "55667788, 20, 50",  // Csődben Lévő Kft.
            "44556677, 20, 50",  // Hátralékos és Csődös Kft.
            "77889900, 20, 50",  // Hiányos Bevallású Kft.
            "22334455, 20, 50",  // Friss Startup Kft.
    })
    void fixtureCompanyHasCorrectInvoiceCount(String taxNumber, int minCount, int maxCount) {
        List<DemoInvoiceFixtures.InvoiceFixture> invoices = DemoInvoiceFixtures.getInvoices(taxNumber);

        assertThat(invoices).isNotEmpty();
        assertThat(invoices.size()).isBetween(minCount, maxCount);
    }

    @Test
    @DisplayName("suspended company has fewer invoices (pre-suspension only)")
    void suspendedCompanyHasFewerInvoices() {
        List<DemoInvoiceFixtures.InvoiceFixture> invoices = DemoInvoiceFixtures.getInvoices("33445566");

        // Suspended company has only a few invoices before suspension
        assertThat(invoices).isNotEmpty();
        assertThat(invoices.size()).isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("unknown tax number returns empty invoice list")
    void unknownTaxNumberReturnsEmptyList() {
        List<DemoInvoiceFixtures.InvoiceFixture> invoices = DemoInvoiceFixtures.getInvoices("00000000");

        assertThat(invoices).isEmpty();
    }

    @Test
    @DisplayName("invoices contain both OUTBOUND and INBOUND directions")
    void invoicesContainBothDirections() {
        List<DemoInvoiceFixtures.InvoiceFixture> invoices = DemoInvoiceFixtures.getInvoices("12345678");

        long outbound = invoices.stream().filter(i -> "OUTBOUND".equals(i.direction())).count();
        long inbound = invoices.stream().filter(i -> "INBOUND".equals(i.direction())).count();

        assertThat(outbound).isGreaterThan(0);
        assertThat(inbound).isGreaterThan(0);
    }

    @Test
    @DisplayName("invoice line items have valid VTSZ codes and Hungarian descriptions")
    void invoiceLineItemsHaveVtszCodesAndDescriptions() {
        List<DemoInvoiceFixtures.InvoiceFixture> invoices = DemoInvoiceFixtures.getInvoices("12345678");

        for (DemoInvoiceFixtures.InvoiceFixture invoice : invoices) {
            assertThat(invoice.lineItems()).isNotEmpty();
            assertThat(invoice.invoiceNumber()).isNotBlank();
            assertThat(invoice.issueDate()).isNotNull();

            for (DemoInvoiceFixtures.LineItemFixture item : invoice.lineItems()) {
                assertThat(item.vtszCode()).isNotBlank().matches("\\d{8}");
                assertThat(item.description()).isNotBlank();
                assertThat(item.quantity()).isPositive();
                assertThat(item.unitPrice()).isPositive();
                assertThat(item.netAmount()).isPositive();
                assertThat(item.vatRate()).isNotNull();
            }
        }
    }

    @ParameterizedTest(name = "all invoices for company {0} fall within the previous quarter")
    @DisplayName("all invoice dates are within the previous quarter (dynamic)")
    @CsvSource({
            "12345678",  // Példa Kereskedelmi Kft.
            "99887766",  // Megbízható Építő Zrt.
            "11223344",  // Adós Szolgáltató Bt.
            "55667788",  // Csődben Lévő Kft.
            "44556677",  // Hátralékos és Csődös Kft.
            "33445566",  // Felfüggesztett Adószámú Kft.
            "77889900",  // Hiányos Bevallású Kft.
            "22334455",  // Friss Startup Kft.
    })
    void allInvoiceDatesAreWithinPreviousQuarter(String taxNumber) {
        LocalDate qStart = DemoInvoiceFixtures.previousQuarterStart();
        LocalDate qEnd = DemoInvoiceFixtures.previousQuarterEnd();

        List<DemoInvoiceFixtures.InvoiceFixture> invoices = DemoInvoiceFixtures.getInvoices(taxNumber);
        assertThat(invoices).isNotEmpty();

        for (DemoInvoiceFixtures.InvoiceFixture invoice : invoices) {
            assertThat(invoice.issueDate())
                    .as("Invoice %s for company %s should be in previous quarter (%s–%s)",
                            invoice.invoiceNumber(), taxNumber, qStart, qEnd)
                    .isAfterOrEqualTo(qStart)
                    .isBeforeOrEqualTo(qEnd);
        }
    }

    @Test
    @DisplayName("invoice fixtures cover mixed VAT rates (27%, 18%, 5%)")
    void invoiceFixturesCoverMixedVatRates() {
        // Csődben Lévő Kft. (food manufacturing) has mixed VAT rates
        List<DemoInvoiceFixtures.InvoiceFixture> invoices = DemoInvoiceFixtures.getInvoices("55667788");

        var vatRates = invoices.stream()
                .flatMap(i -> i.lineItems().stream())
                .map(DemoInvoiceFixtures.LineItemFixture::vatRate)
                .collect(java.util.stream.Collectors.toSet());

        // Food manufacturing company should have both standard (27%) and reduced rates
        assertThat(vatRates).hasSizeGreaterThanOrEqualTo(2);
    }
}
