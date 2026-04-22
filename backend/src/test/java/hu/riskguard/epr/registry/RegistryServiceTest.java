package hu.riskguard.epr.registry;

import hu.riskguard.epr.aggregation.domain.AggregationCacheInvalidator;
import hu.riskguard.epr.audit.AuditService;
import hu.riskguard.epr.audit.AuditSource;
import hu.riskguard.epr.audit.events.FieldChangeEvent;
import hu.riskguard.epr.producer.domain.ProducerProfileService;
import hu.riskguard.epr.registry.domain.*;
import hu.riskguard.epr.registry.internal.RegistryRepository;
import hu.riskguard.jooq.tables.records.ProductPackagingComponentsRecord;
import hu.riskguard.jooq.tables.records.ProductsRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RegistryService} with mocked repositories.
 * Covers create-with-components, update-with-diff-audit, update-no-op, list-with-filters,
 * cross-tenant-404, archive-transitions, component-order-normalisation, PPWR-nullable-roundtrip.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistryServiceTest {

    @Mock
    private RegistryRepository registryRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private ProducerProfileService producerProfileService;

    @Mock
    private AggregationCacheInvalidator aggregationCacheInvalidator;

    private RegistryService registryService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID COMPONENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        registryService = new RegistryService(registryRepository, auditService,
                producerProfileService, aggregationCacheInvalidator);
        // Default mocked scope lookup → UNKNOWN (simple fallback) so existing create() tests
        // do not trip on the new Story 10.11 default-scope resolution path.
        when(producerProfileService.getDefaultEprScope(any())).thenReturn("UNKNOWN");
    }

    // ─── Test 1: create with components emits audit rows ─────────────────────

    @Test
    void create_withComponents_persistsAndEmitsAuditRows() {
        UUID newProductId = UUID.randomUUID();
        UUID newCompId = UUID.randomUUID();
        ComponentUpsertCommand comp = new ComponentUpsertCommand(
                null, "PET bottle", "11010101", new BigDecimal("0.45"),
                0, new BigDecimal("1"), 1, null,
                null, null, null, null, null, null, null, null);
        ProductUpsertCommand cmd = new ProductUpsertCommand(
                "ART-001", "Activia 125g", "3923", "pcs",
                ProductStatus.ACTIVE, List.of(comp));

        when(registryRepository.insertProduct(eq(TENANT_ID), eq(cmd), any())).thenReturn(newProductId);
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
        verify(auditService, atLeastOnce()).recordRegistryFieldChange(argThat(ev ->
                ev != null
                        && ev.productId().equals(newProductId)
                        && ev.tenantId().equals(TENANT_ID)
                        && ev.fieldChanged().contains("CREATE.")
                        && USER_ID.equals(ev.changedByUserId())
                        && ev.source() == AuditSource.MANUAL));
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
                0, new BigDecimal("1"), 1, null,
                null, null, null, null, null, null, null, null);
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
        ArgumentCaptor<FieldChangeEvent> captor = ArgumentCaptor.forClass(FieldChangeEvent.class);
        verify(auditService, atLeastOnce()).recordRegistryFieldChange(captor.capture());
        List<String> fields = captor.getAllValues().stream().map(FieldChangeEvent::fieldChanged).toList();
        assertThat(fields).contains("name");
        assertThat(fields).doesNotContain("article_number", "vtsz", "primary_unit");
        assertThat(captor.getAllValues()).allMatch(ev ->
                ev.productId().equals(PRODUCT_ID) && ev.tenantId().equals(TENANT_ID)
                        && USER_ID.equals(ev.changedByUserId()) && ev.source() == AuditSource.MANUAL);
    }

    // ─── Test 3: update with no changes produces zero audit rows ─────────────

    @Test
    void update_noChanges_producesZeroAuditRows() {
        ProductsRecord existingRecord = buildProductRecord(PRODUCT_ID,
                new ProductUpsertCommand("ART-001", "Activia", "3923", "pcs",
                        ProductStatus.ACTIVE, List.of()));
        ComponentUpsertCommand comp = new ComponentUpsertCommand(
                COMPONENT_ID, "PET", "11010101", new BigDecimal("0.70"),
                0, new BigDecimal("1"), 1, null,
                null, null, null, null, null, null, null, null);
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
        verify(auditService, never()).recordRegistryFieldChange(any());
        verify(registryRepository, never()).updateProduct(any(), any(), any());
    }

    // ─── Test 3b: component_order change is audited per-field ────────────────

    @Test
    void update_componentOrderChanged_emitsComponentOrderAuditRow() {
        ProductsRecord existingRecord = buildProductRecord(PRODUCT_ID,
                new ProductUpsertCommand("ART-001", "X", null, "pcs", ProductStatus.ACTIVE, List.of()));
        ComponentUpsertCommand existingComp = new ComponentUpsertCommand(
                COMPONENT_ID, "Box", null, new BigDecimal("0.50"),
                0, new BigDecimal("1"), 1, null,
                null, null, null, null, null, null, null, null);
        ProductPackagingComponentsRecord existingCompRecord = buildComponentRecord(COMPONENT_ID, PRODUCT_ID, existingComp);

        when(registryRepository.findProductByIdAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(existingRecord));
        when(registryRepository.findComponentsByProductAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(List.of(existingCompRecord));

        // Only componentOrder changed: 0 → 2
        ComponentUpsertCommand reordered = new ComponentUpsertCommand(
                COMPONENT_ID, "Box", null, new BigDecimal("0.50"),
                2, new BigDecimal("1"), 1, null,
                null, null, null, null, null, null, null, null);
        ProductUpsertCommand cmd = new ProductUpsertCommand(
                "ART-001", "X", null, "pcs", ProductStatus.ACTIVE, List.of(reordered));

        registryService.update(TENANT_ID, PRODUCT_ID, USER_ID, cmd);

        verify(auditService).recordRegistryFieldChange(argThat(ev ->
                ev.productId().equals(PRODUCT_ID)
                        && ev.tenantId().equals(TENANT_ID)
                        && ev.fieldChanged().contains("component_order")
                        && "0".equals(ev.oldValue())
                        && "2".equals(ev.newValue())
                        && USER_ID.equals(ev.changedByUserId())
                        && ev.source() == AuditSource.MANUAL));
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
        verify(auditService).recordRegistryFieldChange(argThat(ev ->
                ev.productId().equals(PRODUCT_ID)
                        && ev.tenantId().equals(TENANT_ID)
                        && "status".equals(ev.fieldChanged())
                        && "ACTIVE".equals(ev.oldValue())
                        && "ARCHIVED".equals(ev.newValue())
                        && USER_ID.equals(ev.changedByUserId())
                        && ev.source() == AuditSource.MANUAL));
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
        verify(auditService, never()).recordRegistryFieldChange(any());
    }

    // ─── Test 8: PPWR nullable fields roundtrip ───────────────────────────────

    @Test
    void create_withPpwrFields_persistsAllOptionalFields() {
        UUID newProductId = UUID.randomUUID();
        UUID newCompId = UUID.randomUUID();
        ComponentUpsertCommand comp = new ComponentUpsertCommand(
                null, "Recycled PET", "11020101", new BigDecimal("0.30"),
                0, new BigDecimal("1"), 1, null,
                RecyclabilityGrade.A, new BigDecimal("50.00"), true, null, "DECL-001", null, null, null);
        ProductUpsertCommand cmd = new ProductUpsertCommand(
                null, "Green Bottle", "3923", "pcs",
                ProductStatus.DRAFT, List.of(comp));

        when(registryRepository.insertProduct(eq(TENANT_ID), eq(cmd), any())).thenReturn(newProductId);
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
                0, new BigDecimal("1"), 1, null,
                null, null, null, null, null, null, null, null);
        ProductPackagingComponentsRecord existingCompRecord = buildComponentRecord(COMPONENT_ID, PRODUCT_ID, existingComp);

        when(registryRepository.findProductByIdAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(existingRecord));
        when(registryRepository.findComponentsByProductAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(List.of(existingCompRecord));

        // 0.70 vs 0.75 — should detect change
        ComponentUpsertCommand updatedComp = new ComponentUpsertCommand(
                COMPONENT_ID, "Box", null, new BigDecimal("0.75"),
                0, new BigDecimal("1"), 1, null,
                null, null, null, null, null, null, null, null);
        ProductUpsertCommand cmd = new ProductUpsertCommand(
                "ART-001", "X", null, "pcs", ProductStatus.ACTIVE, List.of(updatedComp));

        registryService.update(TENANT_ID, PRODUCT_ID, USER_ID, cmd);

        verify(auditService).recordRegistryFieldChange(argThat(ev ->
                ev.productId().equals(PRODUCT_ID)
                        && ev.tenantId().equals(TENANT_ID)
                        && ev.fieldChanged().contains("weight_per_unit_kg")
                        && "0.70".equals(ev.oldValue())
                        && "0.75".equals(ev.newValue())
                        && USER_ID.equals(ev.changedByUserId())
                        && ev.source() == AuditSource.MANUAL));
    }

    // ─── Story 10.2: MANUAL_WIZARD audit source round-trip ───────────────────

    @Test
    void update_withManualWizardClassificationSource_emitsAuditWithManualWizardSource() {
        // Existing component: kf_code = 11010101
        ProductsRecord existingRecord = buildProductRecord(PRODUCT_ID,
                new ProductUpsertCommand("ART-001", "Activia", "3923", "pcs",
                        ProductStatus.ACTIVE, List.of()));
        ComponentUpsertCommand existingComp = new ComponentUpsertCommand(
                COMPONENT_ID, "PET bottle", "11010101", new BigDecimal("0.45"),
                0, new BigDecimal("1"), 1, null,
                null, null, null, null, null, null, null, null);
        ProductPackagingComponentsRecord existingCompRecord = buildComponentRecord(COMPONENT_ID, PRODUCT_ID, existingComp);

        when(registryRepository.findProductByIdAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(existingRecord));
        when(registryRepository.findComponentsByProductAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(List.of(existingCompRecord));

        // Browse-flow upsert: kfCode changed, classificationSource = "MANUAL_WIZARD".
        // Only the component's kfCode changes — product-level updateProduct is NOT called,
        // only registryRepository.updateComponent(). Hence no updateProduct stubbing here.
        ComponentUpsertCommand browseResolved = new ComponentUpsertCommand(
                COMPONENT_ID, "PET bottle", "12020202", new BigDecimal("0.45"),
                0, new BigDecimal("1"), 1, null,
                null, null, null, null, null,
                "MANUAL_WIZARD", null, null);
        ProductUpsertCommand cmd = new ProductUpsertCommand(
                "ART-001", "Activia", "3923", "pcs",
                ProductStatus.ACTIVE, List.of(browseResolved));

        registryService.update(TENANT_ID, PRODUCT_ID, USER_ID, cmd);

        verify(auditService).recordRegistryFieldChange(argThat(ev ->
                ev.productId().equals(PRODUCT_ID)
                        && ev.tenantId().equals(TENANT_ID)
                        && ev.fieldChanged().contains("kf_code")
                        && "11010101".equals(ev.oldValue())
                        && "12020202".equals(ev.newValue())
                        && USER_ID.equals(ev.changedByUserId())
                        && ev.source() == AuditSource.MANUAL_WIZARD
                        // classification_strategy / model_version stay null for MANUAL_WIZARD
                        // (only AI_SUGGESTED_* sources carry those fields — per RegistryService:314-317)
                        && ev.classificationStrategy() == null
                        && ev.modelVersion() == null));
    }

    @Test
    void update_addNewComponentWithManualWizardSource_emitsCreateAuditsWithManualWizardSource() {
        // Reviewer finding (EC #5): when a NEW component row is added through update()
        // with classificationSource="MANUAL_WIZARD" (common flow: user adds a row and
        // uses Browse before saving), the CREATE audit rows must carry MANUAL_WIZARD,
        // not MANUAL — otherwise the story's compliance contract silently breaks.
        ProductsRecord existingRecord = buildProductRecord(PRODUCT_ID,
                new ProductUpsertCommand("ART-001", "Activia", "3923", "pcs",
                        ProductStatus.ACTIVE, List.of()));
        when(registryRepository.findProductByIdAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(existingRecord));
        when(registryRepository.findComponentsByProductAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(List.of()); // no existing components

        UUID newCompId = UUID.randomUUID();
        ComponentUpsertCommand newComp = new ComponentUpsertCommand(
                null, "PET bottle", "12020202", new BigDecimal("0.45"),
                0, new BigDecimal("1"), 1, null,
                null, null, null, null, null,
                "MANUAL_WIZARD", null, null);
        when(registryRepository.insertComponent(PRODUCT_ID, TENANT_ID, newComp))
                .thenReturn(newCompId);

        ProductUpsertCommand cmd = new ProductUpsertCommand(
                "ART-001", "Activia", "3923", "pcs",
                ProductStatus.ACTIVE, List.of(newComp));

        registryService.update(TENANT_ID, PRODUCT_ID, USER_ID, cmd);

        // The kf_code CREATE audit row must carry MANUAL_WIZARD source.
        verify(auditService).recordRegistryFieldChange(argThat(ev ->
                ev.fieldChanged().equals("CREATE.components[" + newCompId + "].kf_code")
                        && "12020202".equals(ev.newValue())
                        && ev.source() == AuditSource.MANUAL_WIZARD));
    }

    @Test
    void update_addNewComponentWithoutClassificationSource_emitsCreateAuditsWithManualSource() {
        // Regression guard: when a new component is added without classificationSource,
        // the CREATE audits still default to MANUAL (unchanged legacy behaviour).
        ProductsRecord existingRecord = buildProductRecord(PRODUCT_ID,
                new ProductUpsertCommand("ART-001", "Activia", "3923", "pcs",
                        ProductStatus.ACTIVE, List.of()));
        when(registryRepository.findProductByIdAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(existingRecord));
        when(registryRepository.findComponentsByProductAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(List.of());

        UUID newCompId = UUID.randomUUID();
        ComponentUpsertCommand newComp = new ComponentUpsertCommand(
                null, "Glass", "33030303", new BigDecimal("0.25"),
                0, new BigDecimal("1"), 1, null,
                null, null, null, null, null,
                null, null, null);
        when(registryRepository.insertComponent(PRODUCT_ID, TENANT_ID, newComp))
                .thenReturn(newCompId);

        ProductUpsertCommand cmd = new ProductUpsertCommand(
                "ART-001", "Activia", "3923", "pcs",
                ProductStatus.ACTIVE, List.of(newComp));

        registryService.update(TENANT_ID, PRODUCT_ID, USER_ID, cmd);

        verify(auditService).recordRegistryFieldChange(argThat(ev ->
                ev.fieldChanged().equals("CREATE.components[" + newCompId + "].kf_code")
                        && ev.source() == AuditSource.MANUAL));
    }

    @Test
    void update_withUnknownClassificationSource_fallsBackToManual() {
        // Defensive test: unknown enum name must silently fall back to MANUAL
        // (covers RegistryService.java:308-313 IllegalArgumentException swallow).
        ProductsRecord existingRecord = buildProductRecord(PRODUCT_ID,
                new ProductUpsertCommand("ART-001", "Activia", "3923", "pcs",
                        ProductStatus.ACTIVE, List.of()));
        ComponentUpsertCommand existingComp = new ComponentUpsertCommand(
                COMPONENT_ID, "PET", "11010101", new BigDecimal("0.45"),
                0, new BigDecimal("1"), 1, null,
                null, null, null, null, null, null, null, null);
        ProductPackagingComponentsRecord existingCompRecord = buildComponentRecord(COMPONENT_ID, PRODUCT_ID, existingComp);

        when(registryRepository.findProductByIdAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(existingRecord));
        when(registryRepository.findComponentsByProductAndTenant(PRODUCT_ID, TENANT_ID))
                .thenReturn(List.of(existingCompRecord));

        ComponentUpsertCommand garbage = new ComponentUpsertCommand(
                COMPONENT_ID, "PET", "22020202", new BigDecimal("0.45"),
                0, new BigDecimal("1"), 1, null,
                null, null, null, null, null,
                "GARBAGE_VALUE", null, null);
        ProductUpsertCommand cmd = new ProductUpsertCommand(
                "ART-001", "Activia", "3923", "pcs",
                ProductStatus.ACTIVE, List.of(garbage));

        registryService.update(TENANT_ID, PRODUCT_ID, USER_ID, cmd);

        verify(auditService).recordRegistryFieldChange(argThat(ev ->
                ev.fieldChanged().contains("kf_code")
                        && ev.source() == AuditSource.MANUAL));
    }

    // ─── A-P1: cross-tenant materialTemplateId rejection ─────────────────────

    @Test
    void create_withCrossTenantMaterialTemplateId_throws404() {
        UUID templateId = UUID.randomUUID();
        ComponentUpsertCommand comp = new ComponentUpsertCommand(
                null, "PET bottle", "11010101", new BigDecimal("0.45"),
                0, new BigDecimal("1"), 1, templateId,
                null, null, null, null, null, null, null, null);
        ProductUpsertCommand cmd = new ProductUpsertCommand(
                "ART-X", "Cross-tenant", "3923", "pcs", ProductStatus.ACTIVE, List.of(comp));
        UUID newProductId = UUID.randomUUID();

        when(registryRepository.insertProduct(eq(TENANT_ID), eq(cmd), any())).thenReturn(newProductId);
        when(registryRepository.existsMaterialTemplateForTenant(templateId, TENANT_ID)).thenReturn(false);

        assertThatThrownBy(() -> registryService.create(TENANT_ID, USER_ID, cmd))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");

        verify(registryRepository, never()).insertComponent(any(), any(), any());
    }

    @Test
    void create_withMaterialTemplateIdOwnedByTenant_proceeds() {
        UUID templateId = UUID.randomUUID();
        ComponentUpsertCommand comp = new ComponentUpsertCommand(
                null, "PET bottle", "11010101", new BigDecimal("0.45"),
                0, new BigDecimal("1"), 1, templateId,
                null, null, null, null, null, null, null, null);
        ProductUpsertCommand cmd = new ProductUpsertCommand(
                "ART-Y", "Same-tenant template", "3923", "pcs", ProductStatus.ACTIVE, List.of(comp));
        UUID newProductId = UUID.randomUUID();
        UUID newCompId = UUID.randomUUID();

        when(registryRepository.insertProduct(eq(TENANT_ID), eq(cmd), any())).thenReturn(newProductId);
        when(registryRepository.existsMaterialTemplateForTenant(templateId, TENANT_ID)).thenReturn(true);
        when(registryRepository.insertComponent(newProductId, TENANT_ID, comp)).thenReturn(newCompId);
        when(registryRepository.findProductByIdAndTenant(newProductId, TENANT_ID))
                .thenReturn(Optional.of(buildProductRecord(newProductId, cmd)));
        when(registryRepository.findComponentsByProductAndTenant(newProductId, TENANT_ID))
                .thenReturn(List.of(buildComponentRecord(newCompId, newProductId, comp)));

        Product result = registryService.create(TENANT_ID, USER_ID, cmd);

        assertThat(result.id()).isEqualTo(newProductId);
        verify(registryRepository).existsMaterialTemplateForTenant(templateId, TENANT_ID);
        verify(registryRepository).insertComponent(newProductId, TENANT_ID, comp);
    }

    // ─── Story 10.7: summary cache — second call within TTL skips the repo ───

    @Test
    void getSummary_secondCallWithinTtl_returnsCachedValueWithoutReHittingRepository() {
        // AC #24: spy on repository — verify the Caffeine cache actually short-circuits
        // the repository call when the same tenant key is requested twice within the 10s TTL.
        when(registryRepository.countSummary(TENANT_ID))
                .thenReturn(new RegistryRepository.RegistrySummary(7, 4));

        RegistryRepository.RegistrySummary first = registryService.getSummary(TENANT_ID);
        RegistryRepository.RegistrySummary second = registryService.getSummary(TENANT_ID);

        assertThat(first.totalProducts()).isEqualTo(7);
        assertThat(first.productsWithComponents()).isEqualTo(4);
        assertThat(second).isEqualTo(first);
        // Cache hit: repository queried exactly once despite two service calls.
        verify(registryRepository, times(1)).countSummary(TENANT_ID);
    }

    @Test
    void getSummary_differentTenants_eachMissesCacheOnce() {
        // Cache is keyed by tenantId — distinct tenants must NOT share cache entries.
        UUID otherTenant = UUID.randomUUID();
        when(registryRepository.countSummary(TENANT_ID))
                .thenReturn(new RegistryRepository.RegistrySummary(3, 2));
        when(registryRepository.countSummary(otherTenant))
                .thenReturn(new RegistryRepository.RegistrySummary(11, 9));

        registryService.getSummary(TENANT_ID);
        registryService.getSummary(otherTenant);
        registryService.getSummary(TENANT_ID);      // cached
        registryService.getSummary(otherTenant);    // cached

        verify(registryRepository, times(1)).countSummary(TENANT_ID);
        verify(registryRepository, times(1)).countSummary(otherTenant);
    }

    // ─── Story 10.11 AC #6a — cache-invalidation unit tests ──────────────────

    /**
     * AC #6a — {@code updateProductScope} must call
     * {@link AggregationCacheInvalidator#invalidateTenant(UUID)} exactly once per state-changing
     * write. Unit test runs without an active Spring transaction, so the service's
     * transaction-synchronization branch falls back to immediate invocation (verified path).
     */
    @Test
    void updateProductScope_invalidatesAggregationCacheOnce() {
        UUID scopedProductId = UUID.randomUUID();
        ProductsRecord record = new ProductsRecord();
        record.setId(scopedProductId);
        record.setTenantId(TENANT_ID);
        record.setStatus(ProductStatus.ACTIVE.name());
        record.setEprScope("FIRST_PLACER");
        record.setPrimaryUnit("pcs");
        record.setName("P");
        record.setCreatedAt(OffsetDateTime.now());
        record.setUpdatedAt(OffsetDateTime.now());

        when(registryRepository.findProductByIdAndTenant(scopedProductId, TENANT_ID))
                .thenReturn(Optional.of(record));
        when(registryRepository.updateEprScope(scopedProductId, TENANT_ID, "RESELLER")).thenReturn(1);
        when(registryRepository.findComponentsByProductAndTenant(scopedProductId, TENANT_ID))
                .thenReturn(List.of());

        registryService.updateProductScope(TENANT_ID, scopedProductId, USER_ID, "RESELLER");

        verify(aggregationCacheInvalidator, times(1)).invalidateTenant(TENANT_ID);
    }

    /**
     * AC #6a — idempotent {@code updateProductScope} (current value) must NOT invalidate the cache
     * nor emit an audit event. Regression guard for the "PATCH with current scope" idempotency
     * contract (AC #7 last bullet).
     */
    @Test
    void updateProductScope_idempotent_doesNotInvalidateCacheOrAudit() {
        UUID scopedProductId = UUID.randomUUID();
        ProductsRecord record = new ProductsRecord();
        record.setId(scopedProductId);
        record.setTenantId(TENANT_ID);
        record.setStatus(ProductStatus.ACTIVE.name());
        record.setEprScope("RESELLER");
        record.setPrimaryUnit("pcs");
        record.setName("P");
        record.setCreatedAt(OffsetDateTime.now());
        record.setUpdatedAt(OffsetDateTime.now());

        when(registryRepository.findProductByIdAndTenant(scopedProductId, TENANT_ID))
                .thenReturn(Optional.of(record));
        when(registryRepository.findComponentsByProductAndTenant(scopedProductId, TENANT_ID))
                .thenReturn(List.of());

        registryService.updateProductScope(TENANT_ID, scopedProductId, USER_ID, "RESELLER");

        verify(aggregationCacheInvalidator, never()).invalidateTenant(any());
        verify(auditService, never()).recordEprScopeChanged(any(), any(), any(), any(), any());
    }

    /**
     * AC #6a — {@code bulkUpdateProductScopes} invalidates the cache once for any state-changing
     * batch (never per-row).
     */
    @Test
    void bulkUpdateProductScopes_invalidatesCacheOncePerBatch() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        when(registryRepository.loadProductScopes(eq(TENANT_ID), any()))
                .thenReturn(List.of(
                        new RegistryRepository.ProductScopeRow(idA, "FIRST_PLACER", "ACTIVE"),
                        new RegistryRepository.ProductScopeRow(idB, "UNKNOWN", "ACTIVE")));
        when(registryRepository.updateEprScope(any(), eq(TENANT_ID), eq("RESELLER"))).thenReturn(1);

        registryService.bulkUpdateProductScopes(TENANT_ID, USER_ID, List.of(idA, idB), "RESELLER");

        verify(aggregationCacheInvalidator, times(1)).invalidateTenant(TENANT_ID);
    }

    /**
     * AC #6a — idempotent-only batch (every row already at target) must NOT invalidate the cache.
     */
    @Test
    void bulkUpdateProductScopes_allIdempotent_doesNotInvalidateCache() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        when(registryRepository.loadProductScopes(eq(TENANT_ID), any()))
                .thenReturn(List.of(
                        new RegistryRepository.ProductScopeRow(idA, "RESELLER", "ACTIVE"),
                        new RegistryRepository.ProductScopeRow(idB, "RESELLER", "ACTIVE")));

        registryService.bulkUpdateProductScopes(TENANT_ID, USER_ID, List.of(idA, idB), "RESELLER");

        verify(aggregationCacheInvalidator, never()).invalidateTenant(any());
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
        r.setItemsPerParent(cmd.itemsPerParent());
        r.setWrappingLevel(cmd.wrappingLevel());
        r.setMaterialTemplateId(cmd.materialTemplateId());
        r.setRecyclabilityGrade(cmd.recyclabilityGrade() != null ? cmd.recyclabilityGrade().name() : null);
        r.setRecycledContentPct(cmd.recycledContentPct());
        r.setReusable(cmd.reusable());
        r.setSupplierDeclarationRef(cmd.supplierDeclarationRef());
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());
        return r;
    }
}
