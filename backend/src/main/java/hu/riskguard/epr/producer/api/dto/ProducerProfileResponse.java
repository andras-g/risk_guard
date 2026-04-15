package hu.riskguard.epr.producer.api.dto;

import hu.riskguard.epr.producer.domain.ProducerProfile;

import java.util.UUID;

/**
 * Response DTO for the producer profile REST endpoints.
 * Contains all profile fields including the tax number read-joined from nav_tenant_credentials.
 */
public record ProducerProfileResponse(
        UUID id,
        UUID tenantId,
        String legalName,
        String addressCountryCode,
        String addressCity,
        String addressPostalCode,
        String addressStreetName,
        String addressStreetType,
        String addressHouseNumber,
        String kshStatisticalNumber,
        String companyRegistrationNumber,
        String contactName,
        String contactTitle,
        String contactCountryCode,
        String contactPostalCode,
        String contactCity,
        String contactStreetName,
        String contactPhone,
        String contactEmail,
        Integer okirClientId,
        boolean isManufacturer,
        boolean isIndividualPerformer,
        boolean isSubcontractor,
        boolean isConcessionaire,
        String taxNumber
) {

    /**
     * Maps a domain {@link ProducerProfile} to a response DTO.
     */
    public static ProducerProfileResponse from(ProducerProfile p) {
        return new ProducerProfileResponse(
                p.id(), p.tenantId(), p.legalName(),
                p.addressCountryCode(), p.addressCity(), p.addressPostalCode(),
                p.addressStreetName(), p.addressStreetType(), p.addressHouseNumber(),
                p.kshStatisticalNumber(), p.companyRegistrationNumber(),
                p.contactName(), p.contactTitle(),
                p.contactCountryCode(), p.contactPostalCode(), p.contactCity(),
                p.contactStreetName(),
                p.contactPhone(), p.contactEmail(),
                p.okirClientId(),
                p.isManufacturer(), p.isIndividualPerformer(),
                p.isSubcontractor(), p.isConcessionaire(),
                p.taxNumber()
        );
    }
}
