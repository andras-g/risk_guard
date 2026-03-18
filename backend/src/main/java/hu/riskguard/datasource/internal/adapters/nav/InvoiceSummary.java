package hu.riskguard.datasource.internal.adapters.nav;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Summary record for an invoice from the NAV {@code QueryInvoiceDigest} operation.
 * Contains high-level invoice metadata without individual line items.
 *
 * @param invoiceNumber       unique invoice identifier
 * @param invoiceOperation    operation type (CREATE, MODIFY, STORNO)
 * @param supplierTaxNumber   supplier's 8-digit tax number
 * @param supplierName        supplier's registered name
 * @param customerTaxNumber   customer's 8-digit tax number (may be null for non-domestic)
 * @param customerName        customer's name
 * @param invoiceIssueDate    date of invoice issuance
 * @param invoiceDeliveryDate date of delivery/fulfillment
 * @param invoiceNetAmount    total net amount on the invoice
 * @param invoiceCurrency     currency code (e.g., "HUF", "EUR")
 * @param invoiceDirection    whether OUTBOUND (sales) or INBOUND (purchases)
 */
public record InvoiceSummary(
        String invoiceNumber,
        String invoiceOperation,
        String supplierTaxNumber,
        String supplierName,
        String customerTaxNumber,
        String customerName,
        LocalDate invoiceIssueDate,
        LocalDate invoiceDeliveryDate,
        BigDecimal invoiceNetAmount,
        String invoiceCurrency,
        InvoiceDirection invoiceDirection
) {}
