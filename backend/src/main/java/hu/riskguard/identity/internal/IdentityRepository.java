package hu.riskguard.identity.internal;

import hu.riskguard.core.repository.BaseRepository;
import hu.riskguard.identity.domain.Tenant;
import hu.riskguard.identity.domain.TenantMandate;
import hu.riskguard.identity.domain.User;
import hu.riskguard.jooq.tables.records.TenantMandatesRecord;
import hu.riskguard.jooq.tables.records.TenantsRecord;
import hu.riskguard.jooq.tables.records.UsersRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.TENANTS;
import static hu.riskguard.jooq.Tables.TENANT_MANDATES;
import static hu.riskguard.jooq.Tables.USERS;

@Repository
public class IdentityRepository extends BaseRepository {

    public IdentityRepository(DSLContext dsl) {
        super(dsl);
    }

    /**
     * Find user by email — intentionally cross-tenant.
     * Uses dsl.select().from() (not selectFrom()) because email is globally unique
     * and this query must work across all tenants (e.g., during OAuth2 login before
     * tenant context is established).
     */
    public Optional<User> findUserByEmail(String email) {
        return dsl.select(USERS.asterisk())
                .from(USERS)
                .where(USERS.EMAIL.eq(email))
                .fetchOptionalInto(User.class);
    }

    /**
     * Check if user has an active (non-expired) mandate for a specific tenant — intentionally cross-tenant.
     * This must query across all tenants to verify authorization.
     * A mandate is considered active when valid_to is NULL (indefinite) or valid_to > now.
     */
    public boolean hasMandate(UUID userId, UUID tenantId) {
        OffsetDateTime now = OffsetDateTime.now();
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(TENANT_MANDATES)
                        .where(TENANT_MANDATES.ACCOUNTANT_USER_ID.eq(userId))
                        .and(TENANT_MANDATES.TENANT_ID.eq(tenantId))
                        .and(TENANT_MANDATES.VALID_TO.isNull().or(TENANT_MANDATES.VALID_TO.gt(now)))
        );
    }

    /**
     * Find all tenants a user has active (non-expired) mandates for — intentionally cross-tenant.
     * Returns domain Tenant objects across all active mandates, not scoped to active tenant.
     * Expired mandates (valid_to < now) are excluded.
     * Note: Returns domain Tenant, not api.dto.TenantResponse — the service facade handles DTO conversion.
     */
    public List<Tenant> findMandatedTenants(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now();
        return dsl.select(TENANTS.ID, TENANTS.NAME, TENANTS.TIER)
                .from(TENANTS)
                .join(TENANT_MANDATES).on(TENANT_MANDATES.TENANT_ID.eq(TENANTS.ID))
                .where(TENANT_MANDATES.ACCOUNTANT_USER_ID.eq(userId))
                .and(TENANT_MANDATES.VALID_TO.isNull().or(TENANT_MANDATES.VALID_TO.gt(now)))
                .fetchInto(Tenant.class);
    }

    /**
     * Check if a user exists with the given email — intentionally cross-tenant.
     */
    public boolean existsByEmail(String email) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(USERS)
                        .where(USERS.EMAIL.eq(email))
        );
    }

    /**
     * Find the SSO provider for a user by email — intentionally cross-tenant.
     * Returns empty if user does not exist.
     */
    public Optional<String> findSsoProviderByEmail(String email) {
        return dsl.select(USERS.SSO_PROVIDER)
                .from(USERS)
                .where(USERS.EMAIL.eq(email))
                .fetchOptional(USERS.SSO_PROVIDER);
    }

    public void updatePreferredLanguage(UUID userId, String language) {
        dsl.update(USERS)
                .set(USERS.PREFERRED_LANGUAGE, language)
                .where(USERS.ID.eq(userId))
                .execute();
    }

    /**
     * Find all tenant IDs where a user has currently active mandates — intentionally cross-tenant.
     * Used by notification module's PortfolioController to aggregate alerts across all
     * mandated tenants for an accountant's Portfolio Pulse feed.
     *
     * <p>A mandate is active when: {@code valid_from <= now} AND ({@code valid_to IS NULL} OR {@code valid_to >= now}).
     *
     * @param userId the accountant's user ID
     * @param now    current timestamp for mandate validity check
     * @return list of tenant UUIDs with active mandates
     */
    public List<UUID> findActiveMandateTenantIds(UUID userId, OffsetDateTime now) {
        return dsl.select(TENANT_MANDATES.TENANT_ID)
                .from(TENANT_MANDATES)
                .where(TENANT_MANDATES.ACCOUNTANT_USER_ID.eq(userId))
                .and(TENANT_MANDATES.VALID_FROM.le(now))
                .and(TENANT_MANDATES.VALID_TO.isNull().or(TENANT_MANDATES.VALID_TO.ge(now)))
                .fetch(TENANT_MANDATES.TENANT_ID);
    }

    /**
     * Lightweight query returning only the tier column for a tenant.
     * Used by TierGateInterceptor via IdentityService facade.
     */
    public Optional<String> findTenantTier(UUID tenantId) {
        return dsl.select(TENANTS.TIER)
                .from(TENANTS)
                .where(TENANTS.ID.eq(tenantId))
                .fetchOptional(TENANTS.TIER);
    }

    /**
     * Find a user's email address by user ID — intentionally cross-tenant.
     * Used by notification module's OutboxProcessor to resolve recipient email.
     */
    public Optional<String> findEmailById(UUID userId) {
        return dsl.select(USERS.EMAIL)
                .from(USERS)
                .where(USERS.ID.eq(userId))
                .fetchOptional(USERS.EMAIL);
    }

    /**
     * Find a user's preferred language by user ID — intentionally cross-tenant.
     * Used by notification module's EmailTemplateRenderer for localization.
     */
    public Optional<String> findPreferredLanguageById(UUID userId) {
        return dsl.select(USERS.PREFERRED_LANGUAGE)
                .from(USERS)
                .where(USERS.ID.eq(userId))
                .fetchOptional(USERS.PREFERRED_LANGUAGE);
    }

    @Transactional
    public Tenant saveTenant(Tenant tenant) {
        TenantsRecord record = dsl.newRecord(TENANTS);
        record.from(tenant);
        dsl.insertInto(TENANTS).set(record).execute();
        return record.into(Tenant.class);
    }

    @Transactional
    public User saveUser(User user) {
        UsersRecord record = dsl.newRecord(USERS);
        record.from(user);
        dsl.insertInto(USERS).set(record).execute();
        return record.into(User.class);
    }

    @Transactional
    public void saveTenantMandate(TenantMandate mandate) {
        TenantMandatesRecord record = dsl.newRecord(TENANT_MANDATES);
        record.from(mandate);
        dsl.insertInto(TENANT_MANDATES).set(record).execute();
    }
}
