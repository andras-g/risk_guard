package hu.riskguard.epr.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single line in the invoice auto-fill result.
 * Represents aggregated quantity for a VTSZ code group from outbound invoices.
 *
 * @param vtszCode             the VTSZ product code from invoice line items
 * @param description          material description from the vtszMappings config entry
 * @param suggestedKfCode      suggested KF code from vtszMappings (null if no mapping found)
 * @param aggregatedQuantity   total quantity across all matched invoice lines for this VTSZ code
 * @param unitOfMeasure        unit of measure from invoice line items
 * @param hasExistingTemplate  true if a material template with matching name exists for the tenant
 * @param existingTemplateId   UUID of the matching template (null if hasExistingTemplate is false)
 */
public record InvoiceAutoFillLineDto(
        String vtszCode,
        String description,
        String suggestedKfCode,
        BigDecimal aggregatedQuantity,
        String unitOfMeasure,
        boolean hasExistingTemplate,
        UUID existingTemplateId
) {}
