package hu.riskguard.epr.registry;

import hu.riskguard.datasource.domain.*;
import hu.riskguard.epr.audit.AuditSource;
import hu.riskguard.epr.registry.classifier.ClassificationConfidence;
import hu.riskguard.epr.registry.classifier.ClassificationResult;
import hu.riskguard.epr.registry.classifier.ClassificationStrategy;
import hu.riskguard.epr.registry.classifier.KfCodeClassifierService;
import hu.riskguard.epr.registry.classifier.KfSuggestion;
import hu.riskguard.epr.registry.domain.*;
import hu.riskguard.epr.registry.internal.BootstrapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RegistryBootstrapService}.
 * Mocks DataSourceService, KfCodeClassifierService, BootstrapRepository, and RegistryService.
 */
@ExtendWith(MockitoExtension.class)
class BootstrapServiceTest {

    @Mock private DataSourceService dataSourceService;
    @Mock private KfCodeClassifierService classifierService;
    @Mock private BootstrapRepository bootstrapRepository;
    @Mock private RegistryService registryService;

    private RegistryBootstrapService bootstrapService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2025, 1, 1);
    private static final LocalDate TO = LocalDate.of(2025, 12, 31);
    private static final String TAX_NUMBER = "12345678";

    @BeforeEach
    void setUp() {
        // Synchronous test txManager: TransactionTemplate.execute() still invokes the callback;
        // getTransaction()/commit()/rollback() are no-ops. Covers tests that don't exercise the
        // insert-batch path (approve, reject, navUnavailable) without requiring Mockito stubbing.
        org.springframework.transaction.PlatformTransactionManager txManager =
                new org.springframework.transaction.PlatformTransactionManager() {
                    @Override
                    public org.springframework.transaction.TransactionStatus getTransaction(
                            org.springframework.transaction.TransactionDefinition def) {
                        return new org.springframework.transaction.support.SimpleTransactionStatus();
                    }
                    @Override
                    public void commit(org.springframework.transaction.TransactionStatus status) { /* no-op */ }
                    @Override
                    public void rollback(org.springframework.transaction.TransactionStatus status) { /* no-op */ }
                };
        bootstrapService = new RegistryBootstrapService(
                dataSourceService, classifierService, bootstrapRepository, registryService, txManager);
    }

    // ─── Test 1: happy path — 3 invoice lines, 2 unique dedup keys ───────────

    @Test
    void triggerBootstrap_happyPath_createsCandidates() {
        when(dataSourceService.getTenantTaxNumber(TENANT_ID)).thenReturn(Optional.of(TAX_NUMBER));

        InvoiceSummary s1 = buildSummary("INV-001");
        InvoiceSummary s2 = buildSummary("INV-002");
        when(dataSourceService.queryInvoices(TAX_NUMBER, FROM, TO, InvoiceDirection.OUTBOUND))
                .thenReturn(new InvoiceQueryResult(List.of(s1, s2), true));

        // INV-001 has 2 unique products, INV-002 has 1 product (same as first from INV-001 → dedup)
        InvoiceLineItem item1 = buildLine("Aktivia 125g", "39239090", new BigDecimal("100"));
        InvoiceLineItem item2 = buildLine("PET palack 1L", "39239090", new BigDecimal("50"));
        when(dataSourceService.queryInvoiceDetails("INV-001"))
                .thenReturn(buildDetail("INV-001", List.of(item1, item2)));
        // Same product as item1 — will be merged into the same dedup group
        InvoiceLineItem item3 = buildLine("Aktivia 125g", "39239090", new BigDecimal("200"));
        when(dataSourceService.queryInvoiceDetails("INV-002"))
                .thenReturn(buildDetail("INV-002", List.of(item3)));

        when(bootstrapRepository.existsByTenantAndDedupeKey(eq(TENANT_ID), anyString(), any()))
                .thenReturn(false);
        when(classifierService.classify(anyString(), any())).thenReturn(ClassificationResult.empty());
        when(bootstrapRepository.insertCandidateIfNew(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(true);

        BootstrapResult result = bootstrapService.triggerBootstrap(TENANT_ID, USER_ID, FROM, TO);

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(0);
        verify(classifierService, times(2)).classify(anyString(), any());
        verify(bootstrapRepository, times(2)).insertCandidateIfNew(any(), any(), any(), anyInt(), any(), any(), any());
    }

    // ─── Test 2: dedup merges same product+vtsz across multiple invoice lines ─

    @Test
    void triggerBootstrap_dedup_mergesLineItemsByKey() {
        when(dataSourceService.getTenantTaxNumber(TENANT_ID)).thenReturn(Optional.of(TAX_NUMBER));
        InvoiceSummary s1 = buildSummary("INV-001");
        when(dataSourceService.queryInvoices(TAX_NUMBER, FROM, TO, InvoiceDirection.OUTBOUND))
                .thenReturn(new InvoiceQueryResult(List.of(s1), true));

        // 3 lines with same productName + vtsz → should merge to 1 dedup group, frequency=3
        InvoiceLineItem line1 = buildLine("Termék A", "12345678", new BigDecimal("10"));
        InvoiceLineItem line2 = buildLine("Termék A", "12345678", new BigDecimal("20"));
        InvoiceLineItem line3 = buildLine("Termék A", "12345678", new BigDecimal("30"));
        when(dataSourceService.queryInvoiceDetails("INV-001"))
                .thenReturn(buildDetail("INV-001", List.of(line1, line2, line3)));

        when(bootstrapRepository.existsByTenantAndDedupeKey(eq(TENANT_ID), anyString(), any()))
                .thenReturn(false);
        when(classifierService.classify(anyString(), any())).thenReturn(ClassificationResult.empty());
        when(bootstrapRepository.insertCandidateIfNew(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(true);

        BootstrapResult result = bootstrapService.triggerBootstrap(TENANT_ID, USER_ID, FROM, TO);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(0);

        // Verify frequency=3 and totalQuantity=60 were passed
        ArgumentCaptor<Integer> freqCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<BigDecimal> qtyCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(bootstrapRepository).insertCandidateIfNew(any(), any(), any(),
                freqCaptor.capture(), qtyCaptor.capture(), any(), any());

        assertThat(freqCaptor.getValue()).isEqualTo(3);
        assertThat(qtyCaptor.getValue()).isEqualByComparingTo(new BigDecimal("60"));
    }

    // ─── Test 3: re-trigger skips existing candidate ─────────────────────────

    @Test
    void triggerBootstrap_retrigger_skipsExisting() {
        when(dataSourceService.getTenantTaxNumber(TENANT_ID)).thenReturn(Optional.of(TAX_NUMBER));
        InvoiceSummary s1 = buildSummary("INV-001");
        when(dataSourceService.queryInvoices(TAX_NUMBER, FROM, TO, InvoiceDirection.OUTBOUND))
                .thenReturn(new InvoiceQueryResult(List.of(s1), true));

        InvoiceLineItem line = buildLine("Termék A", "12345678", new BigDecimal("10"));
        when(dataSourceService.queryInvoiceDetails("INV-001"))
                .thenReturn(buildDetail("INV-001", List.of(line)));

        // Candidate already exists → skip
        when(bootstrapRepository.existsByTenantAndDedupeKey(eq(TENANT_ID), anyString(), any()))
                .thenReturn(true);

        BootstrapResult result = bootstrapService.triggerBootstrap(TENANT_ID, USER_ID, FROM, TO);

        assertThat(result.created()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(1);
        // Classifier must NOT be called for skipped candidates
        verify(classifierService, never()).classify(anyString(), any());
        verify(bootstrapRepository, never()).insertCandidateIfNew(any(), any(), any(), anyInt(), any(), any(), any());
    }

    // ─── Test 4: rejected candidate is also skipped — not re-created ─────────

    @Test
    void triggerBootstrap_skipsRejected_doesNotRecreate() {
        when(dataSourceService.getTenantTaxNumber(TENANT_ID)).thenReturn(Optional.of(TAX_NUMBER));
        InvoiceSummary s1 = buildSummary("INV-001");
        when(dataSourceService.queryInvoices(TAX_NUMBER, FROM, TO, InvoiceDirection.OUTBOUND))
                .thenReturn(new InvoiceQueryResult(List.of(s1), true));

        InvoiceLineItem line = buildLine("Nem saját csomagolás", "12345678", new BigDecimal("5"));
        when(dataSourceService.queryInvoiceDetails("INV-001"))
                .thenReturn(buildDetail("INV-001", List.of(line)));

        // existsByTenantAndDedupeKey returns true — simulating a REJECTED_NOT_OWN_PACKAGING row
        when(bootstrapRepository.existsByTenantAndDedupeKey(eq(TENANT_ID), anyString(), any()))
                .thenReturn(true);

        BootstrapResult result = bootstrapService.triggerBootstrap(TENANT_ID, USER_ID, FROM, TO);

        assertThat(result.skipped()).isEqualTo(1);
        verify(bootstrapRepository, never()).insertCandidateIfNew(any(), any(), any(), anyInt(), any(), any(), any());
    }

    // ─── Test 4b: multi-layer classification result forwarded to repository ──

    @Test
    void triggerBootstrap_multiLayerClassification_passedToRepository() {
        when(dataSourceService.getTenantTaxNumber(TENANT_ID)).thenReturn(Optional.of(TAX_NUMBER));
        InvoiceSummary s1 = buildSummary("INV-001");
        when(dataSourceService.queryInvoices(TAX_NUMBER, FROM, TO, InvoiceDirection.OUTBOUND))
                .thenReturn(new InvoiceQueryResult(List.of(s1), true));

        InvoiceLineItem line = buildLine("PET palack 0,5L", "39239090", new BigDecimal("100"));
        when(dataSourceService.queryInvoiceDetails("INV-001"))
                .thenReturn(buildDetail("INV-001", List.of(line)));

        when(bootstrapRepository.existsByTenantAndDedupeKey(eq(TENANT_ID), anyString(), any()))
                .thenReturn(false);

        // Multi-layer classification result with primary + secondary
        KfSuggestion primary = new KfSuggestion("11010101", "PET palack", 0.85, "primary",
                new BigDecimal("0.025"), 1);
        KfSuggestion secondary = new KfSuggestion("41010201", "Karton multipack", 0.70, "secondary",
                new BigDecimal("0.050"), 6);
        ClassificationResult multiLayerResult = new ClassificationResult(
                List.of(primary, secondary), ClassificationStrategy.VERTEX_GEMINI,
                ClassificationConfidence.MEDIUM, "gemini-3.0-flash-preview",
                java.time.Instant.now(), 150, 80);
        when(classifierService.classify(anyString(), any())).thenReturn(multiLayerResult);
        when(bootstrapRepository.insertCandidateIfNew(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(true);

        bootstrapService.triggerBootstrap(TENANT_ID, USER_ID, FROM, TO);

        // Verify the classification result with suggestions is forwarded to the repository
        ArgumentCaptor<ClassificationResult> classCaptor = ArgumentCaptor.forClass(ClassificationResult.class);
        verify(bootstrapRepository).insertCandidateIfNew(eq(TENANT_ID), anyString(), anyString(),
                anyInt(), any(), anyString(), classCaptor.capture());

        ClassificationResult captured = classCaptor.getValue();
        assertThat(captured.suggestions()).hasSize(2);
        assertThat(captured.suggestions().get(0).kfCode()).isEqualTo("11010101");
        assertThat(captured.suggestions().get(0).layer()).isEqualTo("primary");
        assertThat(captured.suggestions().get(0).weightEstimateKg()).isEqualByComparingTo("0.025");
        assertThat(captured.suggestions().get(1).kfCode()).isEqualTo("41010201");
        assertThat(captured.suggestions().get(1).layer()).isEqualTo("secondary");
        assertThat(captured.suggestions().get(1).unitsPerProduct()).isEqualTo(6);
    }

    // ─── Test 5: NAV service unavailable → throws 503 ───────────────────────

    @Test
    void triggerBootstrap_navUnavailable_throwsServiceUnavailable() {
        when(dataSourceService.getTenantTaxNumber(TENANT_ID)).thenReturn(Optional.of(TAX_NUMBER));
        when(dataSourceService.queryInvoices(TAX_NUMBER, FROM, TO, InvoiceDirection.OUTBOUND))
                .thenReturn(new InvoiceQueryResult(List.of(), false));

        assertThatThrownBy(() -> bootstrapService.triggerBootstrap(TENANT_ID, USER_ID, FROM, TO))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    // ─── Test 6: approve happy path ──────────────────────────────────────────

    @Test
    void approve_happyPath_createsProductWithNavBootstrapSource() {
        UUID candidateId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        BootstrapCandidate pendingCandidate = buildPendingCandidate(candidateId);
        BootstrapCandidate approvedCandidate = buildApprovedCandidate(candidateId, productId);

        when(bootstrapRepository.findByIdAndTenant(candidateId, TENANT_ID))
                .thenReturn(Optional.of(pendingCandidate))  // first call (load)
                .thenReturn(Optional.of(approvedCandidate)); // second call (return updated)

        Product mockProduct = buildProduct(productId);
        ApproveCommand cmd = buildApproveCommand();

        // Capture the AuditSource passed to RegistryService.create()
        ArgumentCaptor<AuditSource> sourceCaptor = ArgumentCaptor.forClass(AuditSource.class);
        when(registryService.create(eq(TENANT_ID), eq(USER_ID), any(), sourceCaptor.capture()))
                .thenReturn(mockProduct);
        when(bootstrapRepository.updateCandidateStatus(any(), any(), any(), any(), any()))
                .thenReturn(1);

        BootstrapCandidate result = bootstrapService.approveCandidateAndCreateProduct(
                TENANT_ID, candidateId, USER_ID, cmd);

        assertThat(result.status()).isEqualTo(BootstrapCandidateStatus.APPROVED);
        assertThat(result.resultingProductId()).isEqualTo(productId);
        assertThat(sourceCaptor.getValue()).isEqualTo(AuditSource.NAV_BOOTSTRAP);
        verify(bootstrapRepository).updateCandidateStatus(
                TENANT_ID, candidateId, BootstrapCandidateStatus.APPROVED, productId,
                BootstrapCandidateStatus.PENDING);
    }

    // ─── Test 7: approve already-approved → 409 Conflict ────────────────────

    @Test
    void approve_alreadyApproved_throwsConflict() {
        UUID candidateId = UUID.randomUUID();
        UUID existingProductId = UUID.randomUUID();
        BootstrapCandidate alreadyApproved = buildApprovedCandidate(candidateId, existingProductId);

        when(bootstrapRepository.findByIdAndTenant(candidateId, TENANT_ID))
                .thenReturn(Optional.of(alreadyApproved));

        assertThatThrownBy(() -> bootstrapService.approveCandidateAndCreateProduct(
                TENANT_ID, candidateId, USER_ID, buildApproveCommand()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ─── Test 8: reject persists status, never deletes ────────────────────────

    @Test
    void reject_persistsStatusNeverDeletes() {
        UUID candidateId = UUID.randomUUID();
        BootstrapCandidate pending = buildPendingCandidate(candidateId);
        when(bootstrapRepository.findByIdAndTenant(candidateId, TENANT_ID))
                .thenReturn(Optional.of(pending));
        when(bootstrapRepository.updateCandidateStatus(any(), any(), any(), any(), any()))
                .thenReturn(1);

        bootstrapService.rejectCandidate(TENANT_ID, candidateId, USER_ID,
                BootstrapCandidateStatus.REJECTED_NOT_OWN_PACKAGING);

        verify(bootstrapRepository).updateCandidateStatus(
                TENANT_ID, candidateId, BootstrapCandidateStatus.REJECTED_NOT_OWN_PACKAGING, null,
                BootstrapCandidateStatus.PENDING);
        // No delete call should ever occur
        verifyNoMoreInteractions(bootstrapRepository);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private InvoiceSummary buildSummary(String invoiceNumber) {
        return new InvoiceSummary(invoiceNumber, "CREATE", TAX_NUMBER, "Supplier Kft.",
                "87654321", "Customer Zrt.", LocalDate.of(2025, 6, 1),
                LocalDate.of(2025, 6, 1), new BigDecimal("100000"), "HUF",
                InvoiceDirection.OUTBOUND);
    }

    private InvoiceLineItem buildLine(String description, String vtsz, BigDecimal qty) {
        return new InvoiceLineItem(1, description, qty, "DARAB",
                new BigDecimal("1000"), qty.multiply(new BigDecimal("1000")),
                qty.multiply(new BigDecimal("1000")), vtsz, "VTSZ", vtsz);
    }

    private InvoiceDetail buildDetail(String invoiceNumber, List<InvoiceLineItem> items) {
        return new InvoiceDetail(invoiceNumber, "CREATE", TAX_NUMBER, "Supplier Kft.",
                "87654321", "Customer Zrt.", LocalDate.of(2025, 6, 1),
                LocalDate.of(2025, 6, 1), new BigDecimal("100000"), "HUF",
                InvoiceDirection.OUTBOUND, items, "TRANSFER", java.util.Map.of());
    }

    private BootstrapCandidate buildPendingCandidate(UUID candidateId) {
        return new BootstrapCandidate(candidateId, TENANT_ID, "TERMÉK A", "12345678",
                1, new BigDecimal("10"), "DARAB", BootstrapCandidateStatus.PENDING,
                null, null, null, null, null,
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now());
    }

    private BootstrapCandidate buildApprovedCandidate(UUID candidateId, UUID productId) {
        return new BootstrapCandidate(candidateId, TENANT_ID, "TERMÉK A", "12345678",
                1, new BigDecimal("10"), "DARAB", BootstrapCandidateStatus.APPROVED,
                null, null, null, null, productId,
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now());
    }

    private Product buildProduct(UUID productId) {
        return new Product(productId, TENANT_ID, "ART-001", "Termék A", "12345678",
                "DARAB", ProductStatus.ACTIVE, List.of(),
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now());
    }

    private ApproveCommand buildApproveCommand() {
        ComponentUpsertCommand comp = new ComponentUpsertCommand(
                null, "PET", "11010101", new BigDecimal("0.1"), 0,
                new BigDecimal("1"), 1, null,
                null, null, null, null, null, null, null, null);
        return new ApproveCommand("ART-001", "Termék A", "12345678", "DARAB",
                ProductStatus.ACTIVE, List.of(comp));
    }
}
