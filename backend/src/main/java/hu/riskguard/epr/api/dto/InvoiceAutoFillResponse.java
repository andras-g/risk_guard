package hu.riskguard.epr.api.dto;

import java.util.List;

/**
 * Response for the invoice auto-fill endpoint.
 *
 * @param lines          aggregated invoice lines grouped by VTSZ code with suggested KF codes
 * @param navAvailable   true if NAV Online Számla was reachable; false if any exception occurred
 * @param dataSourceMode current data source mode ("demo", "test", or "live")
 */
public record InvoiceAutoFillResponse(
        List<InvoiceAutoFillLineDto> lines,
        boolean navAvailable,
        String dataSourceMode
) {

    /**
     * Factory method required by NamingConventionTest (Response DTOs must have static from()).
     */
    public static InvoiceAutoFillResponse from(List<InvoiceAutoFillLineDto> lines,
                                               boolean navAvailable,
                                               String dataSourceMode) {
        return new InvoiceAutoFillResponse(lines, navAvailable, dataSourceMode);
    }
}
