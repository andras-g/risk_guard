package hu.riskguard.epr.registry;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static hu.riskguard.jooq.Tables.PRODUCTS;
import static hu.riskguard.jooq.Tables.REGISTRY_ENTRY_AUDIT_LOG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 10.2 AC #2: verifies Flyway migration {@code V20260419_001__add_manual_wizard_to_audit_source.sql}
 * actually landed on the Testcontainers Postgres and that the CHECK constraint on
 * {@code registry_entry_audit_log.source} now accepts {@code MANUAL_WIZARD} while still
 * rejecting unknown values.
 *
 * <p>Flyway applies the entire migration chain on container boot (see
 * {@link ProductPackagingComponentsEpic10MigrationTest} for the same pattern), so this
 * test observes the post-10.2 schema.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ManualWizardAuditSourceMigrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private DSLContext dsl;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void cleanAndSeedTenant() {
        dsl.deleteFrom(REGISTRY_ENTRY_AUDIT_LOG).execute();
        dsl.deleteFrom(PRODUCTS).execute();

        dsl.insertInto(DSL.table("tenants"))
                .set(DSL.field("id", UUID.class), TENANT_ID)
                .set(DSL.field("name", String.class), "ManualWizard Audit Test Tenant")
                .set(DSL.field("tier", String.class), "PRO_EPR")
                .onConflictDoNothing()
                .execute();
    }

    @Test
    void checkConstraint_acceptsManualWizardSource() {
        UUID productId = insertProduct();

        // After V20260419_001 the CHECK constraint should allow 'MANUAL_WIZARD'.
        // A successful INSERT + returning(ID) is sufficient proof — the CHECK fires on write,
        // and a non-null returned id means the row landed past the constraint.
        UUID auditId = dsl.insertInto(REGISTRY_ENTRY_AUDIT_LOG)
                .set(REGISTRY_ENTRY_AUDIT_LOG.PRODUCT_ID, productId)
                .set(REGISTRY_ENTRY_AUDIT_LOG.TENANT_ID, TENANT_ID)
                .set(REGISTRY_ENTRY_AUDIT_LOG.FIELD_CHANGED, "components[probe].kf_code")
                .set(REGISTRY_ENTRY_AUDIT_LOG.OLD_VALUE, "11010101")
                .set(REGISTRY_ENTRY_AUDIT_LOG.NEW_VALUE, "12020202")
                .set(REGISTRY_ENTRY_AUDIT_LOG.SOURCE, "MANUAL_WIZARD")
                .returning(REGISTRY_ENTRY_AUDIT_LOG.ID)
                .fetchOne(REGISTRY_ENTRY_AUDIT_LOG.ID);

        assertThat(auditId).isNotNull();
    }

    @Test
    void checkConstraint_rejectsUnknownSource() {
        UUID productId = insertProduct();

        assertThatThrownBy(() -> dsl.insertInto(REGISTRY_ENTRY_AUDIT_LOG)
                .set(REGISTRY_ENTRY_AUDIT_LOG.PRODUCT_ID, productId)
                .set(REGISTRY_ENTRY_AUDIT_LOG.TENANT_ID, TENANT_ID)
                .set(REGISTRY_ENTRY_AUDIT_LOG.FIELD_CHANGED, "components[probe].kf_code")
                .set(REGISTRY_ENTRY_AUDIT_LOG.SOURCE, "NOT_A_REAL_SOURCE")
                .execute())
                .isInstanceOf(DataAccessException.class);
    }

    private UUID insertProduct() {
        return dsl.insertInto(PRODUCTS)
                .set(PRODUCTS.TENANT_ID, TENANT_ID)
                .set(PRODUCTS.NAME, "ManualWizard Probe Product")
                .set(PRODUCTS.PRIMARY_UNIT, "pcs")
                .set(PRODUCTS.STATUS, "ACTIVE")
                .returning(PRODUCTS.ID)
                .fetchOne(PRODUCTS.ID);
    }
}
