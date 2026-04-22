package hu.riskguard.epr.producer.internal;

import hu.riskguard.core.repository.BaseRepository;
import hu.riskguard.epr.producer.domain.ProducerProfile;
import hu.riskguard.epr.producer.api.dto.ProducerProfileUpsertRequest;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.PRODUCER_PROFILES;
import static hu.riskguard.jooq.Tables.NAV_TENANT_CREDENTIALS;

/**
 * jOOQ repository for producer profiles.
 * Owns the {@code producer_profiles} table.
 * Joins {@code nav_credentials} to provide the tax number on reads.
 */
@Repository
public class ProducerProfileRepository extends BaseRepository {

    ProducerProfileRepository(DSLContext dsl) {
        super(dsl);
    }

    /**
     * Find a producer profile by tenant ID, joined with NAV credentials for the tax number.
     * Returns empty if no profile exists for the tenant.
     */
    public Optional<ProducerProfile> findByTenantId(UUID tenantId) {
        return dsl.select(
                        PRODUCER_PROFILES.ID,
                        PRODUCER_PROFILES.TENANT_ID,
                        PRODUCER_PROFILES.LEGAL_NAME,
                        PRODUCER_PROFILES.ADDRESS_COUNTRY_CODE,
                        PRODUCER_PROFILES.ADDRESS_CITY,
                        PRODUCER_PROFILES.ADDRESS_POSTAL_CODE,
                        PRODUCER_PROFILES.ADDRESS_STREET_NAME,
                        PRODUCER_PROFILES.ADDRESS_STREET_TYPE,
                        PRODUCER_PROFILES.ADDRESS_HOUSE_NUMBER,
                        PRODUCER_PROFILES.KSH_STATISTICAL_NUMBER,
                        PRODUCER_PROFILES.COMPANY_REGISTRATION_NUMBER,
                        PRODUCER_PROFILES.CONTACT_NAME,
                        PRODUCER_PROFILES.CONTACT_TITLE,
                        PRODUCER_PROFILES.CONTACT_COUNTRY_CODE,
                        PRODUCER_PROFILES.CONTACT_POSTAL_CODE,
                        PRODUCER_PROFILES.CONTACT_CITY,
                        PRODUCER_PROFILES.CONTACT_STREET_NAME,
                        PRODUCER_PROFILES.CONTACT_PHONE,
                        PRODUCER_PROFILES.CONTACT_EMAIL,
                        PRODUCER_PROFILES.OKIR_CLIENT_ID,
                        PRODUCER_PROFILES.IS_MANUFACTURER,
                        PRODUCER_PROFILES.IS_INDIVIDUAL_PERFORMER,
                        PRODUCER_PROFILES.IS_SUBCONTRACTOR,
                        PRODUCER_PROFILES.IS_CONCESSIONAIRE,
                        NAV_TENANT_CREDENTIALS.TAX_NUMBER
                )
                .from(PRODUCER_PROFILES)
                .leftJoin(NAV_TENANT_CREDENTIALS).on(NAV_TENANT_CREDENTIALS.TENANT_ID.eq(PRODUCER_PROFILES.TENANT_ID))
                .where(PRODUCER_PROFILES.TENANT_ID.eq(tenantId))
                .fetchOptional(r -> new ProducerProfile(
                        r.get(PRODUCER_PROFILES.ID),
                        r.get(PRODUCER_PROFILES.TENANT_ID),
                        r.get(PRODUCER_PROFILES.LEGAL_NAME),
                        r.get(PRODUCER_PROFILES.ADDRESS_COUNTRY_CODE),
                        r.get(PRODUCER_PROFILES.ADDRESS_CITY),
                        r.get(PRODUCER_PROFILES.ADDRESS_POSTAL_CODE),
                        r.get(PRODUCER_PROFILES.ADDRESS_STREET_NAME),
                        r.get(PRODUCER_PROFILES.ADDRESS_STREET_TYPE),
                        r.get(PRODUCER_PROFILES.ADDRESS_HOUSE_NUMBER),
                        r.get(PRODUCER_PROFILES.KSH_STATISTICAL_NUMBER),
                        r.get(PRODUCER_PROFILES.COMPANY_REGISTRATION_NUMBER),
                        r.get(PRODUCER_PROFILES.CONTACT_NAME),
                        r.get(PRODUCER_PROFILES.CONTACT_TITLE),
                        r.get(PRODUCER_PROFILES.CONTACT_COUNTRY_CODE),
                        r.get(PRODUCER_PROFILES.CONTACT_POSTAL_CODE),
                        r.get(PRODUCER_PROFILES.CONTACT_CITY),
                        r.get(PRODUCER_PROFILES.CONTACT_STREET_NAME),
                        r.get(PRODUCER_PROFILES.CONTACT_PHONE),
                        r.get(PRODUCER_PROFILES.CONTACT_EMAIL),
                        r.get(PRODUCER_PROFILES.OKIR_CLIENT_ID),
                        Boolean.TRUE.equals(r.get(PRODUCER_PROFILES.IS_MANUFACTURER)),
                        Boolean.TRUE.equals(r.get(PRODUCER_PROFILES.IS_INDIVIDUAL_PERFORMER)),
                        Boolean.TRUE.equals(r.get(PRODUCER_PROFILES.IS_SUBCONTRACTOR)),
                        Boolean.TRUE.equals(r.get(PRODUCER_PROFILES.IS_CONCESSIONAIRE)),
                        r.get(NAV_TENANT_CREDENTIALS.TAX_NUMBER)
                ));
    }

    /**
     * Upsert a producer profile for a tenant (INSERT ON CONFLICT UPDATE).
     * Returns the resulting profile.
     */
    public ProducerProfile upsert(UUID tenantId, ProducerProfileUpsertRequest req) {
        String countryCode = req.addressCountryCode() != null ? req.addressCountryCode() : "HU";
        String contactCountryCode = req.contactCountryCode() != null ? req.contactCountryCode() : "HU";
        boolean manufacturer = Boolean.TRUE.equals(req.isManufacturer());
        boolean individualPerformer = Boolean.TRUE.equals(req.isIndividualPerformer());
        boolean subcontractor = Boolean.TRUE.equals(req.isSubcontractor());
        boolean concessionaire = Boolean.TRUE.equals(req.isConcessionaire());

        dsl.insertInto(PRODUCER_PROFILES)
                .set(PRODUCER_PROFILES.TENANT_ID, tenantId)
                .set(PRODUCER_PROFILES.LEGAL_NAME, req.legalName())
                .set(PRODUCER_PROFILES.ADDRESS_COUNTRY_CODE, countryCode)
                .set(PRODUCER_PROFILES.ADDRESS_CITY, req.addressCity())
                .set(PRODUCER_PROFILES.ADDRESS_POSTAL_CODE, req.addressPostalCode())
                .set(PRODUCER_PROFILES.ADDRESS_STREET_NAME, req.addressStreetName())
                .set(PRODUCER_PROFILES.ADDRESS_STREET_TYPE, req.addressStreetType())
                .set(PRODUCER_PROFILES.ADDRESS_HOUSE_NUMBER, req.addressHouseNumber())
                .set(PRODUCER_PROFILES.KSH_STATISTICAL_NUMBER, req.kshStatisticalNumber())
                .set(PRODUCER_PROFILES.COMPANY_REGISTRATION_NUMBER, req.companyRegistrationNumber())
                .set(PRODUCER_PROFILES.CONTACT_NAME, req.contactName())
                .set(PRODUCER_PROFILES.CONTACT_TITLE, req.contactTitle())
                .set(PRODUCER_PROFILES.CONTACT_COUNTRY_CODE, contactCountryCode)
                .set(PRODUCER_PROFILES.CONTACT_POSTAL_CODE, req.contactPostalCode())
                .set(PRODUCER_PROFILES.CONTACT_CITY, req.contactCity())
                .set(PRODUCER_PROFILES.CONTACT_STREET_NAME, req.contactStreetName())
                .set(PRODUCER_PROFILES.CONTACT_PHONE, req.contactPhone())
                .set(PRODUCER_PROFILES.CONTACT_EMAIL, req.contactEmail())
                .set(PRODUCER_PROFILES.OKIR_CLIENT_ID, req.okirClientId())
                .set(PRODUCER_PROFILES.IS_MANUFACTURER, manufacturer)
                .set(PRODUCER_PROFILES.IS_INDIVIDUAL_PERFORMER, individualPerformer)
                .set(PRODUCER_PROFILES.IS_SUBCONTRACTOR, subcontractor)
                .set(PRODUCER_PROFILES.IS_CONCESSIONAIRE, concessionaire)
                .onConflict(PRODUCER_PROFILES.TENANT_ID)
                .doUpdate()
                .set(PRODUCER_PROFILES.LEGAL_NAME, req.legalName())
                .set(PRODUCER_PROFILES.ADDRESS_COUNTRY_CODE, countryCode)
                .set(PRODUCER_PROFILES.ADDRESS_CITY, req.addressCity())
                .set(PRODUCER_PROFILES.ADDRESS_POSTAL_CODE, req.addressPostalCode())
                .set(PRODUCER_PROFILES.ADDRESS_STREET_NAME, req.addressStreetName())
                .set(PRODUCER_PROFILES.ADDRESS_STREET_TYPE, req.addressStreetType())
                .set(PRODUCER_PROFILES.ADDRESS_HOUSE_NUMBER, req.addressHouseNumber())
                .set(PRODUCER_PROFILES.KSH_STATISTICAL_NUMBER, req.kshStatisticalNumber())
                .set(PRODUCER_PROFILES.COMPANY_REGISTRATION_NUMBER, req.companyRegistrationNumber())
                .set(PRODUCER_PROFILES.CONTACT_NAME, req.contactName())
                .set(PRODUCER_PROFILES.CONTACT_TITLE, req.contactTitle())
                .set(PRODUCER_PROFILES.CONTACT_COUNTRY_CODE, contactCountryCode)
                .set(PRODUCER_PROFILES.CONTACT_POSTAL_CODE, req.contactPostalCode())
                .set(PRODUCER_PROFILES.CONTACT_CITY, req.contactCity())
                .set(PRODUCER_PROFILES.CONTACT_STREET_NAME, req.contactStreetName())
                .set(PRODUCER_PROFILES.CONTACT_PHONE, req.contactPhone())
                .set(PRODUCER_PROFILES.CONTACT_EMAIL, req.contactEmail())
                .set(PRODUCER_PROFILES.OKIR_CLIENT_ID, req.okirClientId())
                .set(PRODUCER_PROFILES.IS_MANUFACTURER, manufacturer)
                .set(PRODUCER_PROFILES.IS_INDIVIDUAL_PERFORMER, individualPerformer)
                .set(PRODUCER_PROFILES.IS_SUBCONTRACTOR, subcontractor)
                .set(PRODUCER_PROFILES.IS_CONCESSIONAIRE, concessionaire)
                .set(PRODUCER_PROFILES.UPDATED_AT, OffsetDateTime.now())
                .execute();

        return findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("Failed to read back upserted producer profile for tenant " + tenantId));
    }

    // ─── Story 10.11: default_epr_scope ──────────────────────────────────────

    /** Load the tenant's default epr_scope. Empty when no {@code producer_profiles} row exists. */
    public Optional<String> findDefaultEprScope(UUID tenantId) {
        return dsl.select(PRODUCER_PROFILES.DEFAULT_EPR_SCOPE)
                .from(PRODUCER_PROFILES)
                .where(PRODUCER_PROFILES.TENANT_ID.eq(tenantId))
                .fetchOptional(0, String.class);
    }

    /**
     * Load the tenant's default epr_scope with a {@code SELECT ... FOR UPDATE} row lock. Used by
     * {@link hu.riskguard.epr.producer.domain.ProducerProfileService#updateDefaultEprScope} to close
     * the TOCTOU between read-old and write-new: a concurrent PATCH is serialised on the producer
     * profile row until the enclosing transaction commits.
     */
    public Optional<String> findDefaultEprScopeForUpdate(UUID tenantId) {
        return dsl.select(PRODUCER_PROFILES.DEFAULT_EPR_SCOPE)
                .from(PRODUCER_PROFILES)
                .where(PRODUCER_PROFILES.TENANT_ID.eq(tenantId))
                .forUpdate()
                .fetchOptional(0, String.class);
    }

    /** Update default_epr_scope for a tenant. Returns row count (0 when no profile exists). */
    public int updateDefaultEprScope(UUID tenantId, String scope) {
        return dsl.update(PRODUCER_PROFILES)
                .set(PRODUCER_PROFILES.DEFAULT_EPR_SCOPE, scope)
                .set(PRODUCER_PROFILES.UPDATED_AT, OffsetDateTime.now())
                .where(PRODUCER_PROFILES.TENANT_ID.eq(tenantId))
                .execute();
    }
}
