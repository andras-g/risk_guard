package hu.riskguard.datasource.domain;

import java.math.BigDecimal;

/**
 * Individual line item within an invoice from the NAV {@code QueryInvoiceData} operation.
 * Contains product/service details including VTSZ codes critical for EPR classification.
 *
 * <p>Placed in the domain package so EPR module can use it via the DataSourceService facade.
 *
 * @param lineNumber           sequential line number within the invoice
 * @param lineDescription      product/service description (typically in Hungarian)
 * @param quantity             quantity of items
 * @param unitOfMeasure        unit of measurement (e.g., "DARAB", "KG", "MÉTER")
 * @param unitPrice            price per unit
 * @param lineNetAmount        net amount for this line (quantity × unitPrice)
 * @param lineNetAmountHUF     net amount in HUF (converted if original currency is foreign)
 * @param vtszCode             VTSZ (Vámtarifa Szám) product code — critical for EPR classification
 * @param productCodeCategory  code system (VTSZ, SZJ, KN, or OWN)
 * @param productCodeValue     the actual product code value in the specified system
 */
public record InvoiceLineItem(
        int lineNumber,
        String lineDescription,
        BigDecimal quantity,
        String unitOfMeasure,
        BigDecimal unitPrice,
        BigDecimal lineNetAmount,
        BigDecimal lineNetAmountHUF,
        String vtszCode,
        String productCodeCategory,
        String productCodeValue
) {}
