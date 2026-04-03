package hu.riskguard.datasource.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Detailed invoice record from the NAV {@code QueryInvoiceData} operation.
 * Extends {@link InvoiceSummary} with individual line items and payment details.
 *
 * <p>Placed in the domain package so EPR module can use it via the DataSourceService facade.
 *
 * @param invoiceNumber       unique invoice identifier
 * @param invoiceOperation    operation type (CREATE, MODIFY, STORNO)
 * @param supplierTaxNumber   supplier's 8-digit tax number
 * @param supplierName        supplier's registered name
 * @param customerTaxNumber   customer's 8-digit tax number (may be null for non-domestic)
 * @param customerName        customer's name
 * @param invoiceIssueDate    date of invoice issuance
 * @param invoiceDeliveryDate date of delivery/fulfillment
 * @param invoiceNetAmount    total net amount
 * @param invoiceCurrency     currency code
 * @param invoiceDirection    whether OUTBOUND or INBOUND
 * @param lineItems           individual invoice line items with product codes and amounts
 * @param paymentMethod       payment method (TRANSFER, CASH, CARD, VOUCHER, OTHER)
 * @param vatRateSummary      aggregated VAT amounts per rate (rate → gross amount)
 */
public record InvoiceDetail(
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
        InvoiceDirection invoiceDirection,
        List<InvoiceLineItem> lineItems,
        String paymentMethod,
        Map<BigDecimal, BigDecimal> vatRateSummary
) {}
