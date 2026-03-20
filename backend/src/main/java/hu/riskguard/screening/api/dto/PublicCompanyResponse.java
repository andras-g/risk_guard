package hu.riskguard.screening.api.dto;

/**
 * Public-safe company data response for SEO gateway stubs.
 * Contains ONLY publicly available company information — NO verdict,
 * NO audit hash, NO tenant data.
 *
 * <p>Used by the unauthenticated {@code GET /api/v1/public/companies/{taxNumber}} endpoint.
 *
 * @param taxNumber   the Hungarian tax number (always present)
 * @param companyName the company display name (nullable — may not be in snapshot data)
 * @param address     the company address (nullable — may not be in snapshot data)
 */
public record PublicCompanyResponse(
        String taxNumber,
        String companyName,
        String address
) {
    public static PublicCompanyResponse from(String taxNumber, String companyName, String address) {
        return new PublicCompanyResponse(taxNumber, companyName, address);
    }
}
