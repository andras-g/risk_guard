package hu.riskguard.identity.internal;

import hu.riskguard.core.repository.BaseRepository;
import hu.riskguard.identity.api.dto.TenantResponse;
import hu.riskguard.identity.domain.Tenant;
import hu.riskguard.identity.domain.TenantMandate;
import hu.riskguard.identity.domain.User;
import hu.riskguard.jooq.tables.records.TenantMandatesRecord;
import hu.riskguard.jooq.tables.records.TenantsRecord;
import hu.riskguard.jooq.tables.records.UsersRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    public Optional<User> findUserByEmail(String email) {
        return dsl.select(USERS.asterisk())
                .from(USERS)
                .where(USERS.EMAIL.eq(email))
                .fetchOptionalInto(User.class);
    }

    public boolean hasMandate(UUID userId, UUID tenantId) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(TENANT_MANDATES)
                        .where(TENANT_MANDATES.ACCOUNTANT_USER_ID.eq(userId))
                        .and(TENANT_MANDATES.TENANT_ID.eq(tenantId))
        );
    }

    public List<TenantResponse> findMandatedTenants(UUID userId) {
        return dsl.select(TENANTS.ID, TENANTS.NAME, TENANTS.TIER)
                .from(TENANTS)
                .join(TENANT_MANDATES).on(TENANT_MANDATES.TENANT_ID.eq(TENANTS.ID))
                .where(TENANT_MANDATES.ACCOUNTANT_USER_ID.eq(userId))
                .fetchInto(TenantResponse.class);
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
