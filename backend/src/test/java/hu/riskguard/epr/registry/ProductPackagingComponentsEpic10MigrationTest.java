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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.EPR_MATERIAL_TEMPLATES;
import static hu.riskguard.jooq.Tables.PRODUCT_PACKAGING_COMPONENTS;
import static hu.riskguard.jooq.Tables.PRODUCTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 10.1 AC #3 + #4: Migration parity + FK semantics for
 * {@code V20260418_001__extend_ppc_for_epic10.sql}.
 *
 * <p>Flyway is configured to run the full migration chain on container start, so this test
 * observes the post-migration schema. Parity is asserted by seeding a minimal product +
 * component (defaults for the new columns) and checking the expected post-migration values.
 * FK semantics are exercised directly: nullable OK, non-existent UUID rejected, referenced
 * template cannot be deleted under ON DELETE RESTRICT.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ProductPackagingComponentsEpic10MigrationTest {

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
        dsl.deleteFrom(PRODUCT_PACKAGING_COMPONENTS).execute();
        dsl.deleteFrom(PRODUCTS).execute();
        dsl.deleteFrom(EPR_MATERIAL_TEMPLATES).execute();

        dsl.insertInto(DSL.table("tenants"))
                .set(DSL.field("id", UUID.class), TENANT_ID)
                .set(DSL.field("name", String.class), "Epic10 Mig Test Tenant")
                .set(DSL.field("tier", String.class), "PRO_EPR")
                .onConflictDoNothing()
                .execute();
    }

    // ─── AC #3 — parity: defaults for pre-existing rows ───────────────────────

    @Test
    void parity_newRowWithoutNewColumns_hasWrappingLevel1_nullTemplate_itemsPerParentOne() {
        UUID productId = insertProduct();

        // Insert a component leaving wrapping_level / material_template_id / items_per_parent to defaults
        // (mirrors how a pre-migration row that carried units_per_product=1 is represented post-migration).
        UUID compId = dsl.insertInto(PRODUCT_PACKAGING_COMPONENTS)
                .set(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID, productId)
                .set(PRODUCT_PACKAGING_COMPONENTS.MATERIAL_DESCRIPTION, "Legacy PET")
                .set(PRODUCT_PACKAGING_COMPONENTS.WEIGHT_PER_UNIT_KG, new BigDecimal("0.025"))
                .returning(PRODUCT_PACKAGING_COMPONENTS.ID)
                .fetchOne(PRODUCT_PACKAGING_COMPONENTS.ID);
        assertThat(compId).isNotNull();

        var row = dsl.selectFrom(PRODUCT_PACKAGING_COMPONENTS)
                .where(PRODUCT_PACKAGING_COMPONENTS.ID.eq(compId))
                .fetchOne();
        assertThat(row).isNotNull();
        assertThat(row.getWrappingLevel()).isEqualTo(1);
        assertThat(row.getMaterialTemplateId()).isNull();
        assertThat(row.getItemsPerParent()).isNotNull();
        assertThat(row.getItemsPerParent().compareTo(BigDecimal.ONE)).isZero();
    }

    @Test
    void parity_itemsPerParentAcceptsFractional_roundTripPreservesScale() {
        UUID productId = insertProduct();

        // 0.5 models a half-pallet cover — only possible after the NUMERIC(12,4) widening.
        UUID compId = dsl.insertInto(PRODUCT_PACKAGING_COMPONENTS)
                .set(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID, productId)
                .set(PRODUCT_PACKAGING_COMPONENTS.MATERIAL_DESCRIPTION, "Pallet cover")
                .set(PRODUCT_PACKAGING_COMPONENTS.WEIGHT_PER_UNIT_KG, new BigDecimal("0.500"))
                .set(PRODUCT_PACKAGING_COMPONENTS.ITEMS_PER_PARENT, new BigDecimal("0.5000"))
                .set(PRODUCT_PACKAGING_COMPONENTS.WRAPPING_LEVEL, 3)
                .returning(PRODUCT_PACKAGING_COMPONENTS.ID)
                .fetchOne(PRODUCT_PACKAGING_COMPONENTS.ID);

        var row = dsl.selectFrom(PRODUCT_PACKAGING_COMPONENTS)
                .where(PRODUCT_PACKAGING_COMPONENTS.ID.eq(compId))
                .fetchOne();
        assertThat(row).isNotNull();
        assertThat(row.getItemsPerParent().compareTo(new BigDecimal("0.5000"))).isZero();
        assertThat(row.getWrappingLevel()).isEqualTo(3);
    }

    @Test
    void constraint_wrappingLevelOutOfRange_rejected() {
        UUID productId = insertProduct();

        assertThatThrownBy(() -> dsl.insertInto(PRODUCT_PACKAGING_COMPONENTS)
                .set(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID, productId)
                .set(PRODUCT_PACKAGING_COMPONENTS.MATERIAL_DESCRIPTION, "Bad level")
                .set(PRODUCT_PACKAGING_COMPONENTS.WEIGHT_PER_UNIT_KG, new BigDecimal("0.025"))
                .set(PRODUCT_PACKAGING_COMPONENTS.WRAPPING_LEVEL, 4)
                .execute())
                .isInstanceOf(DataAccessException.class);
    }

    // ─── AC #4 — FK semantics on material_template_id ─────────────────────────

    @Test
    void fk_nullMaterialTemplateId_allowed() {
        UUID productId = insertProduct();

        UUID compId = dsl.insertInto(PRODUCT_PACKAGING_COMPONENTS)
                .set(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID, productId)
                .set(PRODUCT_PACKAGING_COMPONENTS.MATERIAL_DESCRIPTION, "No template")
                .set(PRODUCT_PACKAGING_COMPONENTS.WEIGHT_PER_UNIT_KG, new BigDecimal("0.010"))
                .set(PRODUCT_PACKAGING_COMPONENTS.MATERIAL_TEMPLATE_ID, (UUID) null)
                .returning(PRODUCT_PACKAGING_COMPONENTS.ID)
                .fetchOne(PRODUCT_PACKAGING_COMPONENTS.ID);
        assertThat(compId).isNotNull();
    }

    @Test
    void fk_validMaterialTemplateId_accepted() {
        UUID productId = insertProduct();
        UUID templateId = insertTemplate("PET bottle");

        UUID compId = dsl.insertInto(PRODUCT_PACKAGING_COMPONENTS)
                .set(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID, productId)
                .set(PRODUCT_PACKAGING_COMPONENTS.MATERIAL_DESCRIPTION, "PET bottle")
                .set(PRODUCT_PACKAGING_COMPONENTS.WEIGHT_PER_UNIT_KG, new BigDecimal("0.025"))
                .set(PRODUCT_PACKAGING_COMPONENTS.MATERIAL_TEMPLATE_ID, templateId)
                .returning(PRODUCT_PACKAGING_COMPONENTS.ID)
                .fetchOne(PRODUCT_PACKAGING_COMPONENTS.ID);
        assertThat(compId).isNotNull();

        var row = dsl.selectFrom(PRODUCT_PACKAGING_COMPONENTS)
                .where(PRODUCT_PACKAGING_COMPONENTS.ID.eq(compId))
                .fetchOne();
        assertThat(row).isNotNull();
        assertThat(row.getMaterialTemplateId()).isEqualTo(templateId);
    }

    @Test
    void fk_nonExistentMaterialTemplateId_rejected() {
        UUID productId = insertProduct();
        UUID ghost = UUID.randomUUID();

        assertThatThrownBy(() -> dsl.insertInto(PRODUCT_PACKAGING_COMPONENTS)
                .set(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID, productId)
                .set(PRODUCT_PACKAGING_COMPONENTS.MATERIAL_DESCRIPTION, "Ghost template")
                .set(PRODUCT_PACKAGING_COMPONENTS.WEIGHT_PER_UNIT_KG, new BigDecimal("0.025"))
                .set(PRODUCT_PACKAGING_COMPONENTS.MATERIAL_TEMPLATE_ID, ghost)
                .execute())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void fk_onDeleteRestrict_preventsDeletingReferencedTemplate() {
        UUID productId = insertProduct();
        UUID templateId = insertTemplate("Locked PET");

        dsl.insertInto(PRODUCT_PACKAGING_COMPONENTS)
                .set(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID, productId)
                .set(PRODUCT_PACKAGING_COMPONENTS.MATERIAL_DESCRIPTION, "Uses template")
                .set(PRODUCT_PACKAGING_COMPONENTS.WEIGHT_PER_UNIT_KG, new BigDecimal("0.025"))
                .set(PRODUCT_PACKAGING_COMPONENTS.MATERIAL_TEMPLATE_ID, templateId)
                .execute();

        assertThatThrownBy(() -> dsl.deleteFrom(EPR_MATERIAL_TEMPLATES)
                .where(EPR_MATERIAL_TEMPLATES.ID.eq(templateId))
                .execute())
                .isInstanceOf(DataAccessException.class);

        // Template still there — RESTRICT blocked the delete
        assertThat(dsl.fetchExists(
                DSL.selectOne().from(EPR_MATERIAL_TEMPLATES)
                        .where(EPR_MATERIAL_TEMPLATES.ID.eq(templateId)))).isTrue();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private UUID insertProduct() {
        return dsl.insertInto(PRODUCTS)
                .set(PRODUCTS.TENANT_ID, TENANT_ID)
                .set(PRODUCTS.NAME, "Epic10 Mig Test Product")
                .set(PRODUCTS.PRIMARY_UNIT, "pcs")
                .set(PRODUCTS.STATUS, "ACTIVE")
                .returning(PRODUCTS.ID)
                .fetchOne(PRODUCTS.ID);
    }

    private UUID insertTemplate(String name) {
        var rec = dsl.insertInto(EPR_MATERIAL_TEMPLATES)
                .set(EPR_MATERIAL_TEMPLATES.TENANT_ID, TENANT_ID)
                .set(EPR_MATERIAL_TEMPLATES.NAME, name)
                .set(EPR_MATERIAL_TEMPLATES.KF_CODE, "11010101")
                .set(EPR_MATERIAL_TEMPLATES.BASE_WEIGHT_GRAMS, new BigDecimal("25"))
                .returning(EPR_MATERIAL_TEMPLATES.ID)
                .fetchOne();
        return rec == null ? null : rec.getId();
    }
}
