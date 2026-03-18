package hu.riskguard.datasource.internal.adapters.nav;

/**
 * Direction of an invoice in the NAV Online Számla system.
 *
 * <p>Corresponds to the {@code invoiceDirection} field in the NAV API v3 schema.
 */
public enum InvoiceDirection {

    /** Invoice issued by the queried company (sales invoice). */
    OUTBOUND,

    /** Invoice received by the queried company (purchase invoice). */
    INBOUND
}
