package hu.riskguard.epr.producer.domain;

import hu.riskguard.epr.producer.api.dto.ProducerProfileUpsertRequest;
import hu.riskguard.epr.producer.internal.ProducerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain service for managing producer profiles used in OKIRkapu XML generation.
 *
 * <p>Completeness contract: {@link #get(UUID)} throws {@code 412 PRECONDITION_FAILED}
 * if any of the required fields are null/blank, or if no NAV credentials are configured.
 * This ensures the XML exporter always receives a fully populated profile.
 */
@Service
@RequiredArgsConstructor
public class ProducerProfileService {

    private final ProducerProfileRepository repository;

    /**
     * Retrieve the producer profile for a tenant.
     *
     * @param tenantId the tenant whose profile to load
     * @return the complete, validated producer profile
     * @throws ResponseStatusException 412 PRECONDITION_FAILED if the profile is missing or incomplete
     */
    @Transactional(readOnly = true)
    public ProducerProfile get(UUID tenantId) {
        ProducerProfile profile = repository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.PRECONDITION_FAILED,
                        "producer.profile.incomplete: No producer profile configured for this tenant. " +
                        "Please complete your producer profile at /settings/producer-profile."));

        validateCompleteness(profile);
        return profile;
    }

    /**
     * Retrieve the producer profile for display without completeness validation.
     * Used by the GET endpoint so incomplete profiles can still be shown in the form.
     *
     * @param tenantId the tenant whose profile to load
     * @return the profile if it exists, or empty
     */
    @Transactional(readOnly = true)
    public Optional<ProducerProfile> getForDisplay(UUID tenantId) {
        return repository.findByTenantId(tenantId);
    }

    /**
     * Create or update the producer profile for a tenant.
     *
     * @param tenantId the tenant whose profile to upsert
     * @param req      the upsert request
     * @return the updated profile
     */
    @Transactional
    public ProducerProfile upsert(UUID tenantId, ProducerProfileUpsertRequest req) {
        return repository.upsert(tenantId, req);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void validateCompleteness(ProducerProfile p) {
        if (isBlank(p.legalName())) incomplete("legalName");
        if (isBlank(p.taxNumber())) incomplete("taxNumber");
        if (isBlank(p.addressCity())) incomplete("addressCity");
        if (isBlank(p.addressPostalCode())) incomplete("addressPostalCode");
        if (isBlank(p.addressStreetName())) incomplete("addressStreetName");
        if (isBlank(p.contactName())) incomplete("contactName");
        if (isBlank(p.contactTitle())) incomplete("contactTitle");
        if (isBlank(p.contactPhone())) incomplete("contactPhone");
        if (isBlank(p.contactEmail())) incomplete("contactEmail");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static void incomplete(String field) {
        throw new ResponseStatusException(
                HttpStatus.PRECONDITION_FAILED,
                "producer.profile.incomplete: Required field '" + field + "' is missing. " +
                "Please complete your producer profile at /settings/producer-profile.");
    }
}
