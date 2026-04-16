package hu.riskguard.epr.registry;

import hu.riskguard.epr.registry.domain.*;
import hu.riskguard.epr.registry.internal.RegistryAuditRepository;
import hu.riskguard.epr.registry.internal.RegistryRepository;
import hu.riskguard.jooq.tables.records.ProductPackagingComponentsRecord;
import hu.riskguard.jooq.tables.records.ProductsRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RegistryService} with mocked repositories.
 * Covers create-with-components, update-with-diff-audit, update-no-op, list-with-filters,
 * cross-tenant-404, archive-transitions, component-order-normalisation, PPWR-nullable-roundtrip.
 */
@ExtendWith(MockitoExtension.class)
class RegistryServiceTest {

    @Mock
    private RegistryRepository registryRepository;

    @Mock
    private RegistryAuditRepository auditRepository;

    private RegistryService registryService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID COMPONENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        registryService = new RegistryService(registryRepository, auditRepository);
    }

    // ─── Test 1: create with components emits audit rows ─────────────────────

    @Test
    void create_withComponents_persistsAndEmitsAuditRows() {
        UUID newProductId = UUID.randomUUID();
        UUID newCompId = UUID.randomUUID();
        ComponentUpsertCommand comp = new ComponentUpsertCommand(
                null, "PET bottle", "11010101", new BigDecimal("0.45"),
                0, 1, null, null, null, null, null, null, null, null);
        ProductUpsertCommand cmd = new ProductUpsertCommand(
                "ART-001", "Activia 125g", "3923", "pcs",
                ProductStatus.ACTIVE, List.of(comp));

        when(registryRepository.insertProduct(TENANT_ID, cmd)).thenReturn(newProductId);
        when(registryRepository.insertComponent(newProductId, TENANT_ID, comp)).thenReturn(newCompId);
        when(registryRepository.findProductByIdAndTenant(newProductId, TENANT_ID))
                .thenReturn(Optional.of(buildProductRecord(newProductId, cmd)));
        when(registryRepository.findComponentsByProductAndTenant(newProductId, TENANT_ID))
                .thenReturn(List.of(buildComponentRecord(newCompId, newProductId, comp)));

        Product result = registryService.create(TENANT_ID, USER_ID, cmd);

        assertThat(result.id()).isEqualTo(newProductId);
        assertThat(result.components()).hasSize(1);
        assertThat(result.components().get(0).materialDescription()).isEqualTo("PET bottle");

        // Audit rows: product fields (name, article, vtsz, primary_unit, status) + component fields
        verify(auditRepository, atLeastOnce()).insertAuditRow(
                eq(newProductId), eq(TENANT_ID), contains("CREATE."), any(), any(), eq(USER_ID), eq(AuditSource.MANUAL));
    }

    // ─── Test 2: update with diff produces audit rows only for changed fields ─

    @Test
    void update_withChangedField_emitsAuditRowOnlyForChangedField() {
        // Existing product: name "OldName"
        ProductsRecord existingRecord = buildProductRecord(PRODUCT_ID,
                new ProductUpsertCommand("ART-001", "OldName", "3923", "pcs",
                        ProductStatus.ACTIVE, List.of()));

        ComponentUpsertCommand existingComp = new ComponentUpsertCommand(
                COMPONENT_ID, "Box", "11010101", new BigDecimal("0.50"),
                0, 1, null, null, null, null, null, null, null, null);
        ProductPackagingComponentsRecord existingCompRecord = buildComponentRecord(COMPONENT_ID, PRODUCT_ID, existingComp);

        when(registryRepository.findProductByIdAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(existingRecord));
        when(registryRepository.findComponentsByProductAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(List.of(existingCompRecord));
        when(registryRepository.updateProduct(eq(PRODUCT_ID), eq(TENANT_ID), any())).thenReturn(1);

        ProductUpsertCommand cmd = new ProductUpsertCommand(
                "ART-001", "NewName", "3923", "pcs",
                ProductStatus.ACTIVE, List.of(existingComp));

        registryService.update(TENANT_ID, PRODUCT_ID, USER_ID, cmd);

        // Should emit audit for "name" change only (not article_number, vtsz, etc.)
        ArgumentCaptor<String> fieldCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditRepository, atLeastOnce()).insertAuditRow(
                eq(PRODUCT_ID), eq(TENANT_ID), fieldCaptor.capture(),
                any(), any(), eq(USER_ID), eq(AuditSource.MANUAL));
        assertThat(fieldCaptor.getAllValues()).contains("name");
        assertThat(fieldCaptor.getAllValues()).doesNotContain("article_number", "vtsz", "primary_unit");
    }

    // ─── Test 3: update with no changes produces zero audit rows ─────────────

    @Test
    void update_noChanges_producesZeroAuditRows() {
        ProductsRecord existingRecord = buildProductRecord(PRODUCT_ID,
                new ProductUpsertCommand("ART-001", "Activia", "3923", "pcs",
                        ProductStatus.ACTIVE, List.of()));
        ComponentUpsertCommand comp = new ComponentUpsertCommand(
                COMPONENT_ID, "PET", "11010101", new BigDecimal("0.70"),
                0, 1, null, null, null, null, null, null, null, null);
        ProductPackagingComponentsRecord existingCompRecord = buildComponentRecord(COMPONENT_ID, PRODUCT_ID, comp);

        when(registryRepository.findProductByIdAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(existingRecord));
        when(registryRepository.findComponentsByProductAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(List.of(existingCompRecord));

        // Identical update — same values
        ProductUpsertCommand cmd = new ProductUpsertCommand(
                "ART-001", "Activia", "3923", "pcs",
                ProductStatus.ACTIVE, List.of(comp));

        registryService.update(TENANT_ID, PRODUCT_ID, USER_ID, cmd);

        // No field diff → no audit rows AND no updateProduct UPDATE (would bump updated_at).
        verifyNoInteractions(auditRepository);
        verify(registryRepository, never()).updateProduct(any(), any(), any());
    }

    // ─── Test 3b: component_order change is audited per-field ────────────────

    @Test
    void update_componentOrderChanged_emitsComponentOrderAuditRow() {
        ProductsRecord existingRecord = buildProductRecord(PRODUCT_ID,
                new ProductUpsertCommand("ART-001", "X", null, "pcs", ProductStatus.ACTIVE, List.of()));
        ComponentUpsertCommand existingComp = new ComponentUpsertCommand(
                COMPONENT_ID, "Box", null, new BigDecimal("0.50"),
                0, 1, null, null, null, null, null, null, null, null);
        ProductPackagingComponentsRecord existingCompRecord = buildComponentRecord(COMPONENT_ID, PRODUCT_ID, existingComp);

        when(registryRepository.findProductByIdAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(existingRecord));
        when(registryRepository.findComponentsByProductAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(List.of(existingCompRecord));

        // Only componentOrder changed: 0 → 2
        ComponentUpsertCommand reordered = new ComponentUpsertCommand(
                COMPONENT_ID, "Box", null, new BigDecimal("0.50"),
                2, 1, null, null, null, null, null, null, null, null);
        ProductUpsertCommand cmd = new ProductUpsertCommand(
                "ART-001", "X", null, "pcs", ProductStatus.ACTIVE, List.of(reordered));

        registryService.update(TENANT_ID, PRODUCT_ID, USER_ID, cmd);

        verify(auditRepository).insertAuditRow(
                eq(PRODUCT_ID), eq(TENANT_ID), contains("component_order"),
                eq("0"), eq("2"), eq(USER_ID), eq(AuditSource.MANUAL));
    }

    // ─── Test 4: list with filters delegates correctly ────────────────────────

    @Test
    void list_withFilters_delegatesToRepository() {
        RegistryListFilter filter = new RegistryListFilter("Activia", "39", null, ProductStatus.ACTIVE);
        when(registryRepository.listByTenantWithFilters(TENANT_ID, filter, 0, 20))
                .thenReturn(List.of());

        List<ProductSummary> result = registryService.list(TENANT_ID, filter, 0, 20);

        assertThat(result).isEmpty();
        verify(registryRepository).listByTenantWithFilters(TENANT_ID, filter, 0, 20);
    }

    // ─── Test 5: cross-tenant 404 ─────────────────────────────────────────────

    @Test
    void get_productBelongsToOtherTenant_throws404() {
        when(registryRepository.findProductByIdAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> registryService.get(TENANT_ID, PRODUCT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    // ─── Test 6: archive transitions status ───────────────────────────────────

    @Test
    void archive_activeProduct_setsArchivedAndAudits() {
        ProductsRecord activeRecord = buildProductRecord(PRODUCT_ID,
                new ProductUpsertCommand("ART-001", "Activia", "3923", "pcs",
                        ProductStatus.ACTIVE, List.of()));
        when(registryRepository.findProductByIdAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(activeRecord));
        when(registryRepository.findComponentsByProductAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(List.of());
        when(registryRepository.archive(PRODUCT_ID, TENANT_ID)).thenReturn(1);

        registryService.archive(TENANT_ID, PRODUCT_ID, USER_ID);

        verify(registryRepository).archive(PRODUCT_ID, TENANT_ID);
        verify(auditRepository).insertAuditRow(
                eq(PRODUCT_ID), eq(TENANT_ID), eq("status"),
                eq("ACTIVE"), eq("ARCHIVED"), eq(USER_ID), eq(AuditSource.MANUAL));
    }

    // ─── Test 7: archive already archived is no-op ────────────────────────────

    @Test
    void archive_alreadyArchived_isIdempotent() {
        ProductsRecord archivedRecord = buildArchivedProductRecord(PRODUCT_ID);
        when(registryRepository.findProductByIdAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(archivedRecord));
        when(registryRepository.findComponentsByProductAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(List.of());

        registryService.archive(TENANT_ID, PRODUCT_ID, USER_ID);

        verify(registryRepository, never()).archive(any(), any());
        verifyNoInteractions(auditRepository);
    }

    // ─── Test 8: PPWR nullable fields roundtrip ───────────────────────────────

    @Test
    void create_withPpwrFields_persistsAllOptionalFields() {
        UUID newProductId = UUID.randomUUID();
        UUID newCompId = UUID.randomUUID();
        ComponentUpsertCommand comp = new ComponentUpsertCommand(
                null, "Recycled PET", "11020101", new BigDecimal("0.30"),
                0, 1, RecyclabilityGrade.A, new BigDecimal("50.00"), true, null, "DECL-001", null, null, null);
        ProductUpsertCommand cmd = new ProductUpsertCommand(
                null, "Green Bottle", "3923", "pcs",
                ProductStatus.DRAFT, List.of(comp));

        when(registryRepository.insertProduct(TENANT_ID, cmd)).thenReturn(newProductId);
        when(registryRepository.insertComponent(newProductId, TENANT_ID, comp)).thenReturn(newCompId);

        ProductPackagingComponentsRecord compRecord = buildComponentRecord(newCompId, newProductId, comp);
        compRecord.setRecyclabilityGrade("A");
        compRecord.setRecycledContentPct(new BigDecimal("50.00"));
        compRecord.setReusable(true);
        compRecord.setSupplierDeclarationRef("DECL-001");

        when(registryRepository.findProductByIdAndTenant(newProductId, TENANT_ID))
                .thenReturn(Optional.of(buildProductRecord(newProductId, cmd)));
        when(registryRepository.findComponentsByProductAndTenant(newProductId, TENANT_ID))
                .thenReturn(List.of(compRecord));

        Product result = registryService.create(TENANT_ID, USER_ID, cmd);

        ProductPackagingComponent resultComp = result.components().get(0);
        assertThat(resultComp.recyclabilityGrade()).isEqualTo(RecyclabilityGrade.A);
        assertThat(resultComp.recycledContentPct()).isEqualByComparingTo("50.00");
        assertThat(resultComp.reusable()).isTrue();
        assertThat(resultComp.supplierDeclarationRef()).isEqualTo("DECL-001");
    }

    // ─── Test 9: component BigDecimal diff uses compareTo not equals ──────────

    @Test
    void update_weightChangeWith0dot70vs0dot700_detectsChange() {
        ProductsRecord existingRecord = buildProductRecord(PRODUCT_ID,
                new ProductUpsertCommand("ART-001", "X", null, "pcs", ProductStatus.ACTIVE, List.of()));
        ComponentUpsertCommand existingComp = new ComponentUpsertCommand(
                COMPONENT_ID, "Box", null, new BigDecimal("0.70"),
                0, 1, null, null, null, null, null, null, null, null);
        ProductPackagingComponentsRecord existingCompRecord = buildComponentRecord(COMPONENT_ID, PRODUCT_ID, existingComp);

        when(registryRepository.findProductByIdAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(existingRecord));
        when(registryRepository.findComponentsByProductAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(List.of(existingCompRecord));

        // 0.70 vs 0.75 — should detect change
        ComponentUpsertCommand updatedComp = new ComponentUpsertCommand(
                COMPONENT_ID, "Box", null, new BigDecimal("0.75"),
                0, 1, null, null, null, null, null, null, null, null);
        ProductUpsertCommand cmd = new ProductUpsertCommand(
                "ART-001", "X", null, "pcs", ProductStatus.ACTIVE, List.of(updatedComp));

        registryService.update(TENANT_ID, PRODUCT_ID, USER_ID, cmd);

        verify(auditRepository).insertAuditRow(
                eq(PRODUCT_ID), eq(TENANT_ID), contains("weight_per_unit_kg"),
                eq("0.70"), eq("0.75"), eq(USER_ID), eq(AuditSource.MANUAL));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ProductsRecord buildProductRecord(UUID productId, ProductUpsertCommand cmd) {
        ProductsRecord r = new ProductsRecord();
        r.setId(productId);
        r.setTenantId(TENANT_ID);
        r.setArticleNumber(cmd.articleNumber());
        r.setName(cmd.name());
        r.setVtsz(cmd.vtsz());
        r.setPrimaryUnit(cmd.primaryUnit() != null ? cmd.primaryUnit() : "pcs");
        r.setStatus(cmd.status() != null ? cmd.status().name() : "ACTIVE");
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());
        return r;
    }

    private ProductsRecord buildArchivedProductRecord(UUID productId) {
        ProductsRecord r = new ProductsRecord();
        r.setId(productId);
        r.setTenantId(TENANT_ID);
        r.setName("Archived Product");
        r.setPrimaryUnit("pcs");
        r.setStatus(ProductStatus.ARCHIVED.name());
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());
        return r;
    }

    private ProductPackagingComponentsRecord buildComponentRecord(UUID compId, UUID productId,
                                                                   ComponentUpsertCommand cmd) {
        ProductPackagingComponentsRecord r = new ProductPackagingComponentsRecord();
        r.setId(compId);
        r.setProductId(productId);
        r.setMaterialDescription(cmd.materialDescription());
        r.setKfCode(cmd.kfCode());
        r.setWeightPerUnitKg(cmd.weightPerUnitKg());
        r.setComponentOrder(cmd.componentOrder());
        r.setUnitsPerProduct(cmd.unitsPerProduct());
        r.setRecyclabilityGrade(cmd.recyclabilityGrade() != null ? cmd.recyclabilityGrade().name() : null);
        r.setRecycledContentPct(cmd.recycledContentPct());
        r.setReusable(cmd.reusable());
        r.setSupplierDeclarationRef(cmd.supplierDeclarationRef());
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());
        return r;
    }
}
