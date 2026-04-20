package hu.riskguard.epr.registry;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
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

import static hu.riskguard.jooq.Tables.EPR_BOOTSTRAP_JOBS;
import static hu.riskguard.jooq.Tables.PRODUCT_PACKAGING_COMPONENTS;
import static hu.riskguard.jooq.Tables.PRODUCTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Story 10.4 AC #5 — Migration parity test for:
 *   V20260420_001__create_epr_bootstrap_jobs.sql
 *   V20260420_002__add_classifier_source_and_review_state.sql
 *   V20260420_003__drop_registry_bootstrap_candidates.sql
 *
 * Verifies via information_schema queries that all columns, CHECK constraints,
 * and indexes exist post-migration. Follows the pattern of
 * {@link ProductPackagingComponentsEpic10MigrationTest}.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class EprBootstrapJobsMigrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private DSLContext dsl;

    // ─── AC #1 — epr_bootstrap_jobs table structure ──────────────────────────

    @Test
    void eprBootstrapJobs_tableExists() {
        boolean exists = dsl.fetchExists(
                DSL.select(DSL.one())
                        .from(DSL.table("information_schema.tables"))
                        .where(DSL.field("table_name").eq("epr_bootstrap_jobs"))
        );
        assertThat(exists).isTrue();
    }

    @Test
    void eprBootstrapJobs_allRequiredColumnsPresent() {
        var columns = dsl.select(DSL.field("column_name", String.class))
                .from(DSL.table("information_schema.columns"))
                .where(DSL.field("table_name").eq("epr_bootstrap_jobs"))
                .fetch(DSL.field("column_name", String.class));

        assertThat(columns).contains(
                "id", "tenant_id", "status", "period_from", "period_to",
                "total_pairs", "classified_pairs", "vtsz_fallback_pairs",
                "unresolved_pairs", "created_products", "deleted_products",
                "triggered_by_user_id", "error_message",
                "created_at", "updated_at", "completed_at"
        );
    }

    @Test
    void eprBootstrapJobs_statusCheckConstraintEnforcesValidValues() {
        UUID tenantId = ensureTenant();

        // Valid status — should succeed
        UUID jobId = dsl.insertInto(EPR_BOOTSTRAP_JOBS)
                .set(EPR_BOOTSTRAP_JOBS.TENANT_ID, tenantId)
                .set(EPR_BOOTSTRAP_JOBS.STATUS, "PENDING")
                .set(EPR_BOOTSTRAP_JOBS.PERIOD_FROM, LocalDate.of(2026, 1, 1))
                .set(EPR_BOOTSTRAP_JOBS.PERIOD_TO, LocalDate.of(2026, 3, 31))
                .returning(EPR_BOOTSTRAP_JOBS.ID)
                .fetchOne(EPR_BOOTSTRAP_JOBS.ID);
        assertThat(jobId).isNotNull();

        // Clean up
        dsl.deleteFrom(EPR_BOOTSTRAP_JOBS).where(EPR_BOOTSTRAP_JOBS.ID.eq(jobId)).execute();

        // Invalid status — CHECK constraint must reject
        assertThatThrownBy(() ->
                dsl.insertInto(EPR_BOOTSTRAP_JOBS)
                        .set(EPR_BOOTSTRAP_JOBS.TENANT_ID, tenantId)
                        .set(EPR_BOOTSTRAP_JOBS.STATUS, "INVALID_STATUS")
                        .set(EPR_BOOTSTRAP_JOBS.PERIOD_FROM, LocalDate.of(2026, 1, 1))
                        .set(EPR_BOOTSTRAP_JOBS.PERIOD_TO, LocalDate.of(2026, 3, 31))
                        .execute())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void eprBootstrapJobs_periodRangeCheckConstraintRejectsPeriodFromAfterPeriodTo() {
        UUID tenantId = ensureTenant();

        assertThatThrownBy(() ->
                dsl.insertInto(EPR_BOOTSTRAP_JOBS)
                        .set(EPR_BOOTSTRAP_JOBS.TENANT_ID, tenantId)
                        .set(EPR_BOOTSTRAP_JOBS.STATUS, "PENDING")
                        .set(EPR_BOOTSTRAP_JOBS.PERIOD_FROM, LocalDate.of(2026, 3, 31))
                        .set(EPR_BOOTSTRAP_JOBS.PERIOD_TO, LocalDate.of(2026, 1, 1))
                        .execute())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void eprBootstrapJobs_inflightPartialIndexExists() {
        boolean exists = dsl.fetchExists(
                DSL.select(DSL.one())
                        .from(DSL.table("pg_indexes"))
                        .where(DSL.field("tablename").eq("epr_bootstrap_jobs"))
                        .and(DSL.field("indexname").eq("idx_epr_bootstrap_jobs_tenant_inflight"))
        );
        assertThat(exists).isTrue();
    }

    @Test
    void eprBootstrapJobs_historyIndexExists() {
        boolean exists = dsl.fetchExists(
                DSL.select(DSL.one())
                        .from(DSL.table("pg_indexes"))
                        .where(DSL.field("tablename").eq("epr_bootstrap_jobs"))
                        .and(DSL.field("indexname").eq("idx_epr_bootstrap_jobs_tenant_created"))
        );
        assertThat(exists).isTrue();
    }

    // ─── AC #2 — classifier_source + review_state columns ────────────────────

    @Test
    void ppc_classifierSourceColumnExists() {
        boolean exists = dsl.fetchExists(
                DSL.select(DSL.one())
                        .from(DSL.table("information_schema.columns"))
                        .where(DSL.field("table_name").eq("product_packaging_components"))
                        .and(DSL.field("column_name").eq("classifier_source"))
        );
        assertThat(exists).isTrue();
    }

    @Test
    void products_reviewStateColumnExists() {
        boolean exists = dsl.fetchExists(
                DSL.select(DSL.one())
                        .from(DSL.table("information_schema.columns"))
                        .where(DSL.field("table_name").eq("products"))
                        .and(DSL.field("column_name").eq("review_state"))
        );
        assertThat(exists).isTrue();
    }

    @Test
    void ppc_classifierSourceCheckRejectsInvalidValue() {
        UUID tenantId = ensureTenant();
        UUID productId = insertProduct(tenantId);

        assertThatThrownBy(() ->
                dsl.insertInto(PRODUCT_PACKAGING_COMPONENTS)
                        .set(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID, productId)
                        .set(PRODUCT_PACKAGING_COMPONENTS.MATERIAL_DESCRIPTION, "Test")
                        .set(DSL.field("classifier_source", String.class), "INVALID_SOURCE")
                        .execute())
                .isInstanceOf(DataAccessException.class);

        dsl.deleteFrom(PRODUCTS).where(PRODUCTS.ID.eq(productId)).execute();
    }

    @Test
    void products_reviewStateCheckRejectsInvalidValue() {
        UUID tenantId = ensureTenant();

        UUID productId = insertProduct(tenantId);
        assertThatThrownBy(() ->
                dsl.update(PRODUCTS)
                        .set(DSL.field("review_state", String.class), "INVALID_STATE")
                        .where(PRODUCTS.ID.eq(productId))
                        .execute())
                .isInstanceOf(DataAccessException.class);

        dsl.deleteFrom(PRODUCTS).where(PRODUCTS.ID.eq(productId)).execute();
    }

    // ─── AC #3 — registry_bootstrap_candidates dropped ───────────────────────

    @Test
    void registryBootstrapCandidates_tableDropped() {
        boolean exists = dsl.fetchExists(
                DSL.select(DSL.one())
                        .from(DSL.table("information_schema.tables"))
                        .where(DSL.field("table_name").eq("registry_bootstrap_candidates"))
        );
        assertThat(exists).isFalse();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private UUID ensureTenant() {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table("tenants"))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("name", String.class), "Bootstrap Mig Test Tenant " + id)
                .set(DSL.field("tier", String.class), "PRO_EPR")
                .onConflictDoNothing()
                .execute();
        return id;
    }

    private UUID insertProduct(UUID tenantId) {
        return dsl.insertInto(PRODUCTS)
                .set(PRODUCTS.TENANT_ID, tenantId)
                .set(PRODUCTS.NAME, "Bootstrap Mig Test Product")
                .set(PRODUCTS.PRIMARY_UNIT, "pcs")
                .set(PRODUCTS.STATUS, "ACTIVE")
                .returning(PRODUCTS.ID)
                .fetchOne(PRODUCTS.ID);
    }
}
