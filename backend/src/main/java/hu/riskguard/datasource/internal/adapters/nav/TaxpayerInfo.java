package hu.riskguard.datasource.internal.adapters.nav;

/**
 * Response record for the NAV {@code QueryTaxpayer} operation.
 * Contains basic taxpayer identification and registration data.
 *
 * <p>Note: {@code QueryTaxpayer} is NOT representation-bound — any technical user
 * can query any tax number, making it usable for partner screening without EGYKE.
 *
 * @param companyName        full registered company name
 * @param shortName          abbreviated company name (if available)
 * @param taxNumber          8-digit Hungarian tax number
 * @param vatCode            VAT group code (middle section of the full tax number)
 * @param address            formatted registered address
 * @param incorporationType  legal form of the entity
 * @param vatGroupMembership whether the taxpayer is a VAT group member
 */
public record TaxpayerInfo(
        String companyName,
        String shortName,
        String taxNumber,
        String vatCode,
        String address,
        IncorporationType incorporationType,
        boolean vatGroupMembership
) {

    /**
     * Legal incorporation type as defined in the NAV API schema.
     */
    public enum IncorporationType {
        ORGANIZATION,
        SELF_EMPLOYED,
        TAXABLE_PERSON
    }
}
