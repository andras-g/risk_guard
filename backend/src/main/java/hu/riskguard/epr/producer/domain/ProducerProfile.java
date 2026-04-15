package hu.riskguard.epr.producer.domain;

import java.util.UUID;

/**
 * Producer identity data required by the OKIRkapu KG:KGYF-NÉ XML report.
 *
 * <p>The {@code taxNumber} field is read-joined from {@code nav_tenant_credentials}
 * at query time — it is not stored in {@code producer_profiles} itself.
 */
public record ProducerProfile(
        UUID id,
        UUID tenantId,
        String legalName,
        // Registered address (SZEKHELY_* in XSD)
        String addressCountryCode,
        String addressCity,
        String addressPostalCode,
        String addressStreetName,
        String addressStreetType,
        String addressHouseNumber,
        // KSH statistical number (NNNNNNNN-TTTT-GGG-MM, 17 chars with dashes)
        String kshStatisticalNumber,
        String companyRegistrationNumber,
        // Contact person (KAPCSTARTO_* in XSD)
        String contactName,
        String contactTitle,
        String contactCountryCode,
        String contactPostalCode,
        String contactCity,
        String contactStreetName,
        String contactPhone,
        String contactEmail,
        // OKIR client number (KUJ field in ADATCSOMAG header)
        Integer okirClientId,
        // EPR role flags
        boolean isManufacturer,
        boolean isIndividualPerformer,
        boolean isSubcontractor,
        boolean isConcessionaire,
        // Joined from nav_tenant_credentials
        String taxNumber
) {}
