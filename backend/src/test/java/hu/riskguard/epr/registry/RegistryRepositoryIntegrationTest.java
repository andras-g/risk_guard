package hu.riskguard.epr.registry;

import hu.riskguard.epr.registry.domain.*;
import hu.riskguard.epr.registry.internal.RegistryRepository;
import org.jooq.DSLContext;
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
import java.util.List;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.PRODUCT_PACKAGING_COMPONENTS;
import static hu.riskguard.jooq.Tables.PRODUCTS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RegistryRepository} with Testcontainers PostgreSQL 17.
 * Verifies tenant isolation: two tenants with the same article_number do not collide;
 * Tenant A cannot read Tenant B's product.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class RegistryRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private RegistryRepository registryRepository;

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Clean registry tables
        dsl.deleteFrom(PRODUCT_PACKAGING_COMPONENTS).execute();
        dsl.deleteFrom(PRODUCTS).execute();

        // Ensure test tenants exist (idempotent)
        insertTenant(TENANT_A, "Tenant A");
        insertTenant(TENANT_B, "Tenant B");
    }

    // ─── Test 1: basic insert and find ───────────────────────────────────────

    @Test
    void insertProduct_andFindByIdAndTenant_returnsProduct() {
        ProductUpsertCommand cmd = new ProductUpsertCommand(
                "ART-001", "Activia 4×125g", "3923", "pcs", ProductStatus.ACTIVE, List.of());

        UUID id = registryRepository.insertProduct(TENANT_A, cmd);
        var found = registryRepository.findProductByIdAndTenant(id, TENANT_A);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Activia 4×125g");
        assertThat(found.get().getArticleNumber()).isEqualTo("ART-001");
        assertThat(found.get().getTenantId()).isEqualTo(TENANT_A);
    }

    // ─── Test 2: tenant isolation — tenant B cannot read tenant A's product ──

    @Test
    void findByIdAndTenant_crossTenant_returnsEmpty() {
        ProductUpsertCommand cmd = new ProductUpsertCommand(
                "ART-999", "Secret Product", null, "pcs", ProductStatus.ACTIVE, List.of());
        UUID id = registryRepository.insertProduct(TENANT_A, cmd);

        var result = registryRepository.findProductByIdAndTenant(id, TENANT_B);

        assertThat(result).isEmpty();
    }

    // ─── Test 3: same article_number for two tenants does not collide ─────────

    @Test
    void sameArticleNumber_differentTenants_noCollision() {
        ProductUpsertCommand cmdA = new ProductUpsertCommand(
                "SHARED-001", "Product A", null, "pcs", ProductStatus.ACTIVE, List.of());
        ProductUpsertCommand cmdB = new ProductUpsertCommand(
                "SHARED-001", "Product B", null, "pcs", ProductStatus.ACTIVE, List.of());

        UUID idA = registryRepository.insertProduct(TENANT_A, cmdA);
        UUID idB = registryRepository.insertProduct(TENANT_B, cmdB);

        assertThat(idA).isNotEqualTo(idB);
        assertThat(registryRepository.findProductByIdAndTenant(idA, TENANT_A)).isPresent();
        assertThat(registryRepository.findProductByIdAndTenant(idB, TENANT_B)).isPresent();
    }

    // ─── Test 4: component tenant isolation via join ──────────────────────────

    @Test
    void findComponentsByProductAndTenant_crossTenant_returnsEmpty() {
        ProductUpsertCommand cmd = new ProductUpsertCommand(
                null, "Bottled Water", "2201", "pcs", ProductStatus.ACTIVE, List.of());
        UUID productId = registryRepository.insertProduct(TENANT_A, cmd);

        ComponentUpsertCommand comp = new ComponentUpsertCommand(
                null, "PET", "11020101", new BigDecimal("0.30"),
                0, null, null, null, null, null, null, null, null);
        registryRepository.insertComponent(productId, TENANT_A, comp);

        // Tenant B should not see Tenant A's components
        List<?> components = registryRepository.findComponentsByProductAndTenant(productId, TENANT_B);
        assertThat(components).isEmpty();
    }

    // ─── Test 5: archive sets status ─────────────────────────────────────────

    @Test
    void archive_setsStatusToArchived() {
        UUID id = registryRepository.insertProduct(TENANT_A,
                new ProductUpsertCommand(null, "Product", null, "pcs", ProductStatus.ACTIVE, List.of()));

        registryRepository.archive(id, TENANT_A);

        var found = registryRepository.findProductByIdAndTenant(id, TENANT_A);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("ARCHIVED");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void insertTenant(UUID id, String name) {
        dsl.insertInto(org.jooq.impl.DSL.table("tenants"))
                .set(org.jooq.impl.DSL.field("id", UUID.class), id)
                .set(org.jooq.impl.DSL.field("name", String.class), name)
                .set(org.jooq.impl.DSL.field("tier", String.class), "PRO_EPR")
                .onConflictDoNothing()
                .execute();
    }
}
