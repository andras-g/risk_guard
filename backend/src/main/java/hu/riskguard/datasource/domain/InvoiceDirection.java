package hu.riskguard.datasource.domain;

/**
 * Direction of an invoice in the NAV Online Számla system.
 *
 * <p>Corresponds to the {@code invoiceDirection} field in the NAV API v3 schema.
 * Placed in the domain package so EPR module can use it via the DataSourceService facade.
 */
public enum InvoiceDirection {

    /** Invoice issued by the queried company (sales invoice). */
    OUTBOUND,

    /** Invoice received by the queried company (purchase invoice). */
    INBOUND
}
