package hu.riskguard.epr.producer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for creating or updating a producer profile.
 * All validatable fields use {@code @NotBlank} for completeness checking;
 * optional XSD fields (kshStatisticalNumber, companyRegistrationNumber) are nullable.
 */
public record ProducerProfileUpsertRequest(
        @NotBlank String legalName,
        String addressCountryCode,
        @NotBlank String addressCity,
        @NotBlank String addressPostalCode,
        @NotBlank String addressStreetName,
        String addressStreetType,
        String addressHouseNumber,
        // KSH statistical number (NNNNNNNN-TTTT-GGG-MM, 17 chars with dashes) — optional at save
        @Pattern(regexp = "^\\d{8}-\\d{4}-\\d{3}-\\d{2}$", message = "KSH statistical number must match NNNNNNNN-TTTT-GGG-MM format")
        String kshStatisticalNumber,
        String companyRegistrationNumber,
        @NotBlank String contactName,
        @NotBlank String contactTitle,
        String contactCountryCode,
        String contactPostalCode,
        String contactCity,
        String contactStreetName,
        @NotBlank String contactPhone,
        @NotBlank String contactEmail,
        Integer okirClientId,
        Boolean isManufacturer,
        Boolean isIndividualPerformer,
        Boolean isSubcontractor,
        Boolean isConcessionaire
) {}
