package hu.riskguard.datasource.internal;

import hu.riskguard.core.repository.BaseRepository;
import org.jooq.DSLContext;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.val;

/**
 * jOOQ repository for the {@code nav_tenant_credentials} table.
 * Uses string-based DSL since the table is added in Story 8.1 and jOOQ codegen
 * runs from a live DB; typed classes will be generated on next {@code ./gradlew generateJooq}.
 */
@Repository
public class NavTenantCredentialRepository extends BaseRepository {

    /**
     * Snapshot of a single tenant credential row (decrypted values NOT stored here —
     * raw encrypted values are returned for the caller to decrypt via {@link AesFieldEncryptor}).
     */
    public record CredentialRow(
            UUID id,
            UUID tenantId,
            String loginEncrypted,
            String passwordHash,
            String signingKeyEnc,
            String exchangeKeyEnc,
            String taxNumber
    ) {}

    public NavTenantCredentialRepository(DSLContext dsl) {
        super(dsl);
    }

    /**
     * Upserts credential fields for the given tenant (insert or update on conflict).
     */
    public void upsert(
            UUID tenantId,
            String loginEncrypted,
            String passwordHash,
            String signingKeyEnc,
            String exchangeKeyEnc,
            String taxNumber
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.execute("""
                INSERT INTO nav_tenant_credentials
                    (tenant_id, login_encrypted, password_hash, signing_key_enc, exchange_key_enc, tax_number, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id) DO UPDATE SET
                    login_encrypted  = EXCLUDED.login_encrypted,
                    password_hash    = EXCLUDED.password_hash,
                    signing_key_enc  = EXCLUDED.signing_key_enc,
                    exchange_key_enc = EXCLUDED.exchange_key_enc,
                    tax_number       = EXCLUDED.tax_number,
                    updated_at       = EXCLUDED.updated_at
                """,
                val(tenantId), val(loginEncrypted), val(passwordHash),
                val(signingKeyEnc), val(exchangeKeyEnc), val(taxNumber),
                val(now, SQLDataType.TIMESTAMPWITHTIMEZONE), val(now, SQLDataType.TIMESTAMPWITHTIMEZONE)
        );
    }

    /**
     * Returns the credential row for the given tenant, or empty if not found.
     */
    public Optional<CredentialRow> findByTenantId(UUID tenantId) {
        return dsl.select(
                        field("id"),
                        field("tenant_id"),
                        field("login_encrypted"),
                        field("password_hash"),
                        field("signing_key_enc"),
                        field("exchange_key_enc"),
                        field("tax_number")
                )
                .from(table("nav_tenant_credentials"))
                .where(field("tenant_id").eq(tenantId))
                .fetchOptional(r -> new CredentialRow(
                        r.get(field("id", UUID.class)),
                        r.get(field("tenant_id", UUID.class)),
                        r.get(field("login_encrypted", String.class)),
                        r.get(field("password_hash", String.class)),
                        r.get(field("signing_key_enc", String.class)),
                        r.get(field("exchange_key_enc", String.class)),
                        r.get(field("tax_number", String.class))
                ));
    }

    /**
     * Returns true if the given tenant has NAV credentials configured.
     */
    public boolean existsByTenantId(UUID tenantId) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(table("nav_tenant_credentials"))
                        .where(field("tenant_id").eq(tenantId))
        );
    }

    /**
     * Deletes credentials for the given tenant. No-op if not found.
     */
    public void deleteByTenantId(UUID tenantId) {
        dsl.deleteFrom(table("nav_tenant_credentials"))
                .where(field("tenant_id").eq(tenantId))
                .execute();
    }
}
