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

    // ─── Story 10.11: default_epr_scope accessors ────────────────────────────

    /**
     * Return the tenant's current {@code default_epr_scope}. When no profile row exists yet,
     * returns {@code "UNKNOWN"} — callers that want to know "does a profile exist" should use
     * {@link #getForDisplay(UUID)}.
     */
    @Transactional(readOnly = true)
    public String getDefaultEprScope(UUID tenantId) {
        return repository.findDefaultEprScope(tenantId).orElse("UNKNOWN");
    }

    /**
     * Update the tenant's {@code default_epr_scope}. Throws {@code 412 PRECONDITION_FAILED} when
     * no profile row exists (the creation flow uses {@link #upsert} — this method is for updating
     * an existing profile).
     *
     * <p>Read-and-write are atomic in one transaction with {@code SELECT ... FOR UPDATE} on the
     * producer-profile row, so a concurrent PATCH cannot corrupt the {@code fromScope} observed by
     * the audit event (review P6). Returns {@link DefaultEprScopeUpdate} carrying both the previous
     * and resulting scope so the caller can emit a consistent audit row.
     */
    @Transactional
    public DefaultEprScopeUpdate updateDefaultEprScope(UUID tenantId, String scope) {
        String previous = repository.findDefaultEprScopeForUpdate(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                        "producer.profile.missing: No producer profile exists for this tenant. " +
                        "Create the profile first at /settings/producer-profile."));
        if (!java.util.Objects.equals(previous, scope)) {
            repository.updateDefaultEprScope(tenantId, scope);
        }
        return new DefaultEprScopeUpdate(previous, scope);
    }

    /** Previous + resulting scope returned from an atomic {@code updateDefaultEprScope} call. */
    public record DefaultEprScopeUpdate(String fromScope, String toScope) {}

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
