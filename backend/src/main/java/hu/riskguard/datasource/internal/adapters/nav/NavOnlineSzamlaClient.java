package hu.riskguard.datasource.internal.adapters.nav;

import java.time.LocalDate;
import java.util.List;

/**
 * Client interface for the NAV Online Számla API v3.
 *
 * <p>This is the contract for the future NAV integration. Implementations will use
 * JAXB-generated classes from the NAV XSD schemas and handle:
 * <ul>
 *   <li>SHA-512 password hashing</li>
 *   <li>SHA3-512 request signature generation</li>
 *   <li>XML request/response marshalling</li>
 *   <li>Credential management per tenant</li>
 * </ul>
 *
 * <p><strong>NOT YET IMPLEMENTED</strong> — stub interface only. Implementation deferred
 * until NAV technical user credentials are available via accountant registration.
 *
 * @see <a href="https://github.com/nav-gov-hu/Online-Invoice">NAV Online Invoice API</a>
 */
public interface NavOnlineSzamlaClient {

    /**
     * Query taxpayer information for a given tax number.
     * Maps to the NAV {@code queryTaxpayer} operation.
     *
     * <p>This operation is NOT representation-bound — any technical user can query
     * any Hungarian tax number without needing EGYKE authorization.
     *
     * @param taxNumber 8-digit Hungarian tax number
     * @return taxpayer identification and registration data
     */
    TaxpayerInfo queryTaxpayer(String taxNumber);

    /**
     * Query invoice digests (summaries) for a company within a date range.
     * Maps to the NAV {@code queryInvoiceDigest} operation.
     *
     * <p>This operation IS representation-bound — requires the querying technical user
     * to have EGYKE representation for the company being queried.
     *
     * @param taxNumber 8-digit tax number of the represented company
     * @param from      start date of the query range (inclusive)
     * @param to        end date of the query range (inclusive)
     * @param direction whether to query OUTBOUND (sales) or INBOUND (purchase) invoices
     * @return list of invoice summaries matching the criteria
     */
    List<InvoiceSummary> queryInvoiceDigest(String taxNumber, LocalDate from, LocalDate to, InvoiceDirection direction);

    /**
     * Query full invoice data including line items for a specific invoice.
     * Maps to the NAV {@code queryInvoiceData} operation.
     *
     * <p>Line items contain VTSZ codes critical for EPR material classification.
     *
     * @param invoiceNumber the unique invoice identifier from NAV
     * @return full invoice with line items, payment method, and VAT summary
     */
    InvoiceDetail queryInvoiceData(String invoiceNumber);
}
