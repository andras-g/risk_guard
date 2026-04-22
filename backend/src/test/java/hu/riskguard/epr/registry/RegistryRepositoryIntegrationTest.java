package hu.riskguard.epr.registry;

import hu.riskguard.epr.producer.internal.ProducerProfileRepository;
import hu.riskguard.epr.registry.domain.*;
import hu.riskguard.epr.registry.internal.RegistryRepository;
import hu.riskguard.epr.registry.internal.RegistryRepository.ExcludedProductRow;
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
import java.util.Set;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.PRODUCER_PROFILES;
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

    @Autowired
    private RegistryService registryService;

    @Autowired
    private ProducerProfileRepository producerProfileRepository;

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID USER_A = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Clean registry tables — order matters (FK from components → products)
        dsl.deleteFrom(PRODUCT_PACKAGING_COMPONENTS).execute();
        dsl.deleteFrom(PRODUCTS).execute();
        dsl.deleteFrom(PRODUCER_PROFILES).where(
                PRODUCER_PROFILES.TENANT_ID.in(TENANT_A, TENANT_B)).execute();

        // Ensure test tenants exist (idempotent)
        insertTenant(TENANT_A, "Tenant A");
        insertTenant(TENANT_B, "Tenant B");

        // Seed one user per tenant so RegistryService.create audit rows satisfy the
        // registry_entry_audit_log.changed_by_user_id FK.
        insertUser(USER_A, TENANT_A, "fixture-a@riskguard.hu");
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
                0, new BigDecimal("1"), 1, null,
                null, null, null, null, null, null, null, null);
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

    // ─── Story 10.11 AC #23 — scope-driven aggregation + default-resolution ───

    /**
     * AC #23 test 1 — {@code loadForAggregation} must drop {@code RESELLER} rows and keep both
     * {@code FIRST_PLACER} and {@code UNKNOWN} rows (compliance-safe include-on-UNKNOWN per AC #3).
     */
    @Test
    void loadForAggregation_excludesResellerProducts() {
        UUID firstPlacerId = registryRepository.insertProduct(TENANT_A,
                new ProductUpsertCommand("ART-FP", "FirstPlacer", "3923", "pcs",
                        ProductStatus.ACTIVE, "FIRST_PLACER", List.of()), "FIRST_PLACER");
        UUID resellerId = registryRepository.insertProduct(TENANT_A,
                new ProductUpsertCommand("ART-RS", "Reseller", "3924", "pcs",
                        ProductStatus.ACTIVE, "RESELLER", List.of()), "RESELLER");
        UUID unknownId = registryRepository.insertProduct(TENANT_A,
                new ProductUpsertCommand("ART-UN", "Unknown", "3925", "pcs",
                        ProductStatus.ACTIVE, "UNKNOWN", List.of()), "UNKNOWN");

        var rows = registryRepository.loadForAggregation(TENANT_A);

        assertThat(rows).extracting(RegistryRepository.AggregationRow::productId)
                .containsExactlyInAnyOrder(firstPlacerId, unknownId)
                .doesNotContain(resellerId);
    }

    /**
     * AC #23 test 2 — {@code loadExcludedResellerProducts} returns the intersection of
     * {@code RESELLER} products and the caller-supplied sold-product set, so products that the
     * tenant re-sells but did NOT sell in the period are not shown on the filing "excluded" panel.
     */
    @Test
    void loadExcludedResellerProducts_returnsOnlyResellerWithSales() {
        UUID soldReseller = registryRepository.insertProduct(TENANT_A,
                new ProductUpsertCommand("ART-SR", "SoldReseller", "7010", "pcs",
                        ProductStatus.ACTIVE, "RESELLER", List.of()), "RESELLER");
        registryRepository.insertProduct(TENANT_A,
                new ProductUpsertCommand("ART-UR", "UnsoldReseller", "7011", "pcs",
                        ProductStatus.ACTIVE, "RESELLER", List.of()), "RESELLER");
        UUID firstPlacerId = registryRepository.insertProduct(TENANT_A,
                new ProductUpsertCommand("ART-FP2", "FirstPlacer2", "7012", "pcs",
                        ProductStatus.ACTIVE, "FIRST_PLACER", List.of()), "FIRST_PLACER");

        List<ExcludedProductRow> excluded = registryRepository.loadExcludedResellerProducts(
                TENANT_A, Set.of(soldReseller, firstPlacerId));

        assertThat(excluded).extracting(ExcludedProductRow::productId)
                .containsExactly(soldReseller);
    }

    /**
     * AC #23 test 3 — {@link RegistryService#create} resolves the tenant's
     * {@code default_epr_scope} from the {@code producer_profiles} row when the caller omits the
     * scope field on the {@link ProductUpsertCommand}.
     */
    @Test
    void insertProduct_defaultsFromProducerProfile() {
        seedProducerProfile(TENANT_A, "FIRST_PLACER");

        Product created = registryService.create(TENANT_A, USER_A,
                new ProductUpsertCommand("ART-DP", "DefaultedProduct", "2202", "pcs",
                        ProductStatus.ACTIVE, List.of()));

        assertThat(created.eprScope()).isEqualTo("FIRST_PLACER");
        String persisted = dsl.select(PRODUCTS.EPR_SCOPE)
                .from(PRODUCTS).where(PRODUCTS.ID.eq(created.id()))
                .fetchOne(0, String.class);
        assertThat(persisted).isEqualTo("FIRST_PLACER");
    }

    /**
     * AC #23 test 4 — when no {@code producer_profiles} row exists for the tenant,
     * {@code RegistryService.create} must fall back to {@code 'UNKNOWN'} (AC #10 fallback contract).
     */
    @Test
    void insertProduct_fallsBackToUnknownWhenNoProfile() {
        // Intentionally no producer-profile insert for TENANT_A.

        Product created = registryService.create(TENANT_A, USER_A,
                new ProductUpsertCommand("ART-FB", "FallbackProduct", "2203", "pcs",
                        ProductStatus.ACTIVE, List.of()));

        assertThat(created.eprScope()).isEqualTo("UNKNOWN");
        String persisted = dsl.select(PRODUCTS.EPR_SCOPE)
                .from(PRODUCTS).where(PRODUCTS.ID.eq(created.id()))
                .fetchOne(0, String.class);
        assertThat(persisted).isEqualTo("UNKNOWN");
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

    private void insertUser(UUID id, UUID tenantId, String email) {
        dsl.insertInto(org.jooq.impl.DSL.table("users"))
                .set(org.jooq.impl.DSL.field("id", UUID.class), id)
                .set(org.jooq.impl.DSL.field("tenant_id", UUID.class), tenantId)
                .set(org.jooq.impl.DSL.field("email", String.class), email)
                .set(org.jooq.impl.DSL.field("role", String.class), "SME_ADMIN")
                .onConflictDoNothing()
                .execute();
    }

    /**
     * Minimal producer-profile seed — only the two columns the
     * default-scope lookup reads ({@code tenant_id}, {@code default_epr_scope}). All other columns
     * fall back to DB defaults / nullable and are not exercised by these tests.
     */
    private void seedProducerProfile(UUID tenantId, String defaultScope) {
        dsl.insertInto(PRODUCER_PROFILES)
                .set(PRODUCER_PROFILES.TENANT_ID, tenantId)
                .set(PRODUCER_PROFILES.LEGAL_NAME, "Fixture Co Kft.")
                .set(PRODUCER_PROFILES.DEFAULT_EPR_SCOPE, defaultScope)
                .execute();
    }
}
