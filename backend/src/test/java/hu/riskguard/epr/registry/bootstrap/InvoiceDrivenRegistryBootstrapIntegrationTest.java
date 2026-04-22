package hu.riskguard.epr.registry.bootstrap;

import hu.riskguard.core.security.TenantContext;
import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.datasource.domain.InvoiceDetail;
import hu.riskguard.datasource.domain.InvoiceDirection;
import hu.riskguard.datasource.domain.InvoiceLineItem;
import hu.riskguard.datasource.domain.InvoiceQueryResult;
import hu.riskguard.datasource.domain.InvoiceSummary;
import hu.riskguard.epr.audit.AuditSource;
import hu.riskguard.epr.producer.domain.ProducerProfileService;
import hu.riskguard.epr.registry.api.dto.BatchPackagingResult;
import hu.riskguard.epr.registry.api.dto.PackagingLayerDto;
import hu.riskguard.epr.registry.bootstrap.domain.BootstrapJobStatus;
import hu.riskguard.epr.registry.bootstrap.domain.InvoiceDrivenRegistryBootstrapService;
import hu.riskguard.epr.registry.bootstrap.internal.BootstrapJobRepository;
import hu.riskguard.epr.registry.domain.BatchPackagingClassifierService;
import hu.riskguard.epr.registry.domain.ClassifierUsageService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.EPR_BOOTSTRAP_JOBS;
import static hu.riskguard.jooq.Tables.PRODUCT_PACKAGING_COMPONENTS;
import static hu.riskguard.jooq.Tables.PRODUCTS;
import static hu.riskguard.jooq.Tables.REGISTRY_ENTRY_AUDIT_LOG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Testcontainers integration test for Story 10.4 AC #34.
 *
 * <p>Seeds a tenant + NAV credentials fixture + producer profile + 20 mocked invoice lines
 * spanning 5 distinct pairs (2 Gemini, 2 fallback, 1 unresolved). Asserts DB state post-run:
 *
 * <ul>
 *   <li>5 product rows with correct {@code review_state} (only the UNRESOLVED pair is flagged)
 *   <li>Component rows tagged with the correct {@code classifier_source}
 *       ({@code AI_SUGGESTED_CONFIRMED} / {@code VTSZ_FALLBACK}; UNRESOLVED pair has zero
 *       components)
 *   <li>Audit trail entries present (via {@code AuditService} on the {@code NAV_BOOTSTRAP} source)
 *   <li>Job row reaches {@code COMPLETED} with the expected counter breakdown
 * </ul>
 *
 * <p>Collaborators {@link DataSourceService}, {@link BatchPackagingClassifierService},
 * {@link ClassifierUsageService}, and {@link ProducerProfileService} are mocked to keep
 * the test hermetic (no live NAV / Vertex AI calls). {@link BootstrapJobRepository},
 * {@link InvoiceDrivenRegistryBootstrapService}, and the real Registry repository run
 * against the Testcontainers Postgres instance — that is the coverage gap the unit-scope
 * {@link InvoiceDrivenRegistryBootstrapServiceTest} cannot fill.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class InvoiceDrivenRegistryBootstrapIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private DataSourceService dataSourceService;
    @MockitoBean private BatchPackagingClassifierService classifierService;
    @MockitoBean private ClassifierUsageService usageService;
    @MockitoBean private ProducerProfileService producerProfileService;

    @Autowired private DSLContext dsl;
    @Autowired private InvoiceDrivenRegistryBootstrapService bootstrapService;
    @Autowired private BootstrapJobRepository bootstrapJobRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID   = UUID.randomUUID();
    private static final String TAX_NUMBER = "12345678-2-11";

    @BeforeEach
    void setUp() {
        // The TenantAwareDSLContext interceptor requires TenantContext to be populated
        // before hitting tenant-aware tables. In production the interceptor chain sets
        // it from the JWT; here we set it explicitly, matching ScreeningServiceIntegrationTest.
        TenantContext.clear();
        TenantContext.setCurrentTenant(TENANT_ID);

        // Isolate state — these tables have FK relationships.
        dsl.deleteFrom(REGISTRY_ENTRY_AUDIT_LOG).execute();
        dsl.deleteFrom(PRODUCT_PACKAGING_COMPONENTS).execute();
        dsl.deleteFrom(PRODUCTS).execute();
        dsl.deleteFrom(EPR_BOOTSTRAP_JOBS).execute();

        insertTenant(TENANT_ID, "Fixture Tenant");
        insertUser(USER_ID, TENANT_ID, "fixture@riskguard.hu");

        // Preflight collaborators
        lenient().when(dataSourceService.getTenantTaxNumber(TENANT_ID))
                .thenReturn(java.util.Optional.of(TAX_NUMBER));
        lenient().doReturn(null).when(producerProfileService).get(TENANT_ID);
        // Story 10.11: bootstrap inserts stamp epr_scope — mocked profile returns UNKNOWN default.
        lenient().when(producerProfileService.getDefaultEprScope(TENANT_ID)).thenReturn("UNKNOWN");
        lenient().when(usageService.getMonthlyCap()).thenReturn(10_000);
        lenient().when(usageService.getCurrentMonthCallCount(TENANT_ID)).thenReturn(0);
    }

    @Test
    void fiveDistinctPairs_twoGeminiTwoFallbackOneUnresolved_producesCorrectDbState() throws Exception {
        // 5 distinct pairs spread across 20 invoice lines (4 lines each — all dedup to same key).
        // Distinct pairs:
        //   vtsz=3923, desc="PET palack 0,5L"  → GEMINI
        //   vtsz=4819, desc="Kartondoboz"       → GEMINI
        //   vtsz=7010, desc="Üvegpalack"        → VTSZ_FALLBACK
        //   vtsz=7612, desc="Alu doboz"         → VTSZ_FALLBACK
        //   vtsz=9999, desc="Ismeretlen árú"    → UNRESOLVED
        List<String[]> pairs = List.of(
                new String[]{"3923", "PET palack 0,5L"},
                new String[]{"4819", "Kartondoboz"},
                new String[]{"7010", "Üvegpalack"},
                new String[]{"7612", "Alu doboz"},
                new String[]{"9999", "Ismeretlen árú"}
        );

        // Build 20 invoice lines (4 copies of each) to verify dedup correctness.
        List<InvoiceLineItem> lines = new java.util.ArrayList<>();
        int seq = 1;
        for (int copy = 0; copy < 4; copy++) {
            for (String[] p : pairs) {
                lines.add(new InvoiceLineItem(seq++, p[1],
                        null, null, null, null, null, p[0], null, null));
            }
        }
        var summary = new InvoiceSummary("INV-INT", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND);
        var detail = new InvoiceDetail("INV-INT", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND, lines, null, null);

        when(dataSourceService.queryInvoices(eq(TAX_NUMBER), any(), any(), eq(InvoiceDirection.OUTBOUND)))
                .thenReturn(new InvoiceQueryResult(List.of(summary), true));
        when(dataSourceService.queryInvoiceDetails("INV-INT")).thenReturn(detail);

        // Classifier returns 1-layer Gemini / VTSZ-fallback / UNRESOLVED mix.
        when(classifierService.classify(any(), eq(TENANT_ID)))
                .thenAnswer(inv -> {
                    List<hu.riskguard.epr.registry.api.dto.BatchPackagingRequest.PairRequest> req = inv.getArgument(0);
                    List<BatchPackagingResult> results = new java.util.ArrayList<>();
                    for (var pairReq : req) {
                        switch (pairReq.vtsz()) {
                            case "3923", "4819" -> results.add(new BatchPackagingResult(
                                    pairReq.vtsz(), pairReq.description(),
                                    List.of(new PackagingLayerDto(1, "11020101",
                                            new BigDecimal("0.05"), 1, pairReq.description())),
                                    BatchPackagingResult.STRATEGY_GEMINI, "v-test-1"));
                            case "7010", "7612" -> results.add(new BatchPackagingResult(
                                    pairReq.vtsz(), pairReq.description(),
                                    // Null weight is legal for VTSZ_FALLBACK (coerced to 0 on persist).
                                    List.of(new PackagingLayerDto(1, "11050101",
                                            null, 1, "fallback")),
                                    BatchPackagingResult.STRATEGY_VTSZ_FALLBACK, "v-test-1"));
                            case "9999" -> results.add(new BatchPackagingResult(
                                    pairReq.vtsz(), pairReq.description(),
                                    List.of(),
                                    BatchPackagingResult.STRATEGY_UNRESOLVED, "v-test-1"));
                            default -> throw new AssertionError("Unexpected pair " + pairReq.vtsz());
                        }
                    }
                    return results;
                });

        UUID jobId = bootstrapService.startJob(TENANT_ID, USER_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        // The @Async worker runs on taskExecutor; poll until terminal.
        awaitTerminal(jobId);

        var jobRecord = bootstrapJobRepository.findByIdAndTenant(jobId, TENANT_ID).orElseThrow();
        assertThat(jobRecord.status()).isEqualTo(BootstrapJobStatus.COMPLETED);
        assertThat(jobRecord.totalPairs()).isEqualTo(5);
        assertThat(jobRecord.classifiedPairs()).isEqualTo(5);
        assertThat(jobRecord.vtszFallbackPairs()).isEqualTo(2);
        assertThat(jobRecord.unresolvedPairs()).isEqualTo(1);
        assertThat(jobRecord.createdProducts()).isEqualTo(5);

        // 5 product rows; UNRESOLVED pair has review_state=MISSING_PACKAGING.
        int productCount = dsl.fetchCount(PRODUCTS, PRODUCTS.TENANT_ID.eq(TENANT_ID));
        assertThat(productCount).isEqualTo(5);

        int missingPackaging = dsl.fetchCount(PRODUCTS,
                PRODUCTS.TENANT_ID.eq(TENANT_ID).and(PRODUCTS.REVIEW_STATE.eq("MISSING_PACKAGING")));
        // VTSZ_FALLBACK layers are seeded with null weight → coerced to 0 → flagged as well.
        // UNRESOLVED is always flagged. Expect 1 (UNRESOLVED) + 2 (zero-weight fallback) = 3.
        assertThat(missingPackaging).isEqualTo(3);

        // Classifier source tagging: 2 AI_SUGGESTED_CONFIRMED, 2 VTSZ_FALLBACK, 0 NAV_BOOTSTRAP.
        int aiComponents = dsl.fetchCount(PRODUCT_PACKAGING_COMPONENTS,
                PRODUCT_PACKAGING_COMPONENTS.CLASSIFIER_SOURCE.eq(AuditSource.AI_SUGGESTED_CONFIRMED.name()));
        int fallbackComponents = dsl.fetchCount(PRODUCT_PACKAGING_COMPONENTS,
                PRODUCT_PACKAGING_COMPONENTS.CLASSIFIER_SOURCE.eq(AuditSource.VTSZ_FALLBACK.name()));
        assertThat(aiComponents).isEqualTo(2);
        assertThat(fallbackComponents).isEqualTo(2);

        // UNRESOLVED pair has zero components (AC #20).
        var unresolvedProductId = dsl.select(PRODUCTS.ID).from(PRODUCTS)
                .where(PRODUCTS.TENANT_ID.eq(TENANT_ID)).and(PRODUCTS.VTSZ.eq("9999"))
                .fetchOne(PRODUCTS.ID);
        assertThat(unresolvedProductId).isNotNull();
        int unresolvedComponentCount = dsl.fetchCount(PRODUCT_PACKAGING_COMPONENTS,
                PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID.eq(unresolvedProductId));
        assertThat(unresolvedComponentCount).isZero();

        // Audit trail: each created product emitted one NAV_BOOTSTRAP "bootstrap.created" event.
        int auditEventCount = dsl.fetchCount(REGISTRY_ENTRY_AUDIT_LOG,
                REGISTRY_ENTRY_AUDIT_LOG.TENANT_ID.eq(TENANT_ID)
                        .and(REGISTRY_ENTRY_AUDIT_LOG.SOURCE.eq(AuditSource.NAV_BOOTSTRAP.name())));
        // 5 created events (one per new product) — no overwrites in this fixture.
        assertThat(auditEventCount).isGreaterThanOrEqualTo(5);

        // Story 10.11 AC #23 — bootstrap-created products inherit the tenant's default_epr_scope
        // (mocked to UNKNOWN in setUp). Negatively asserts any created product is missing a scope.
        int unknownScopeCount = dsl.fetchCount(PRODUCTS,
                PRODUCTS.TENANT_ID.eq(TENANT_ID).and(PRODUCTS.EPR_SCOPE.eq("UNKNOWN")));
        assertThat(unknownScopeCount).isEqualTo(5);
    }

    /**
     * Story 10.11 AC #23 (5th test) — {@code bootstrapCreatedProducts_useProducerProfileDefault}.
     *
     * <p>When the tenant's default scope is {@code FIRST_PLACER}, bootstrap-created products must
     * land with {@code epr_scope='FIRST_PLACER'}. Isolates the scope-stamping path by stubbing the
     * profile default and running a minimal 1-pair flow.
     */
    @Test
    void bootstrapCreatedProducts_useProducerProfileDefault() throws Exception {
        // Re-stub getDefaultEprScope → FIRST_PLACER for this test only.
        lenient().when(producerProfileService.getDefaultEprScope(TENANT_ID)).thenReturn("FIRST_PLACER");

        var summary = new InvoiceSummary("INV-FP", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND);
        var detail = new InvoiceDetail("INV-FP", "CREATE", "12345678", null, null, null,
                LocalDate.now(), null, null, null, InvoiceDirection.OUTBOUND,
                List.of(new InvoiceLineItem(1, "PET palack 0,5L",
                        null, null, null, null, null, "3923", null, null)),
                null, null);

        when(dataSourceService.queryInvoices(eq(TAX_NUMBER), any(), any(), eq(InvoiceDirection.OUTBOUND)))
                .thenReturn(new InvoiceQueryResult(List.of(summary), true));
        when(dataSourceService.queryInvoiceDetails("INV-FP")).thenReturn(detail);

        when(classifierService.classify(any(), eq(TENANT_ID)))
                .thenAnswer(inv -> {
                    List<hu.riskguard.epr.registry.api.dto.BatchPackagingRequest.PairRequest> req = inv.getArgument(0);
                    List<BatchPackagingResult> results = new java.util.ArrayList<>();
                    for (var p : req) {
                        results.add(new BatchPackagingResult(p.vtsz(), p.description(),
                                List.of(new PackagingLayerDto(1, "11020101",
                                        new BigDecimal("0.05"), 1, p.description())),
                                BatchPackagingResult.STRATEGY_GEMINI, "v-test-1"));
                    }
                    return results;
                });

        UUID jobId = bootstrapService.startJob(TENANT_ID, USER_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
        awaitTerminal(jobId);

        assertThat(bootstrapJobRepository.findByIdAndTenant(jobId, TENANT_ID).orElseThrow().status())
                .isEqualTo(BootstrapJobStatus.COMPLETED);

        int firstPlacerCount = dsl.fetchCount(PRODUCTS,
                PRODUCTS.TENANT_ID.eq(TENANT_ID).and(PRODUCTS.EPR_SCOPE.eq("FIRST_PLACER")));
        assertThat(firstPlacerCount).isEqualTo(1);
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

    private void awaitTerminal(UUID jobId) throws InterruptedException {
        // The @Async worker runs on Spring's taskExecutor — poll status until terminal.
        // Max wait 15s — plenty of headroom for local + CI.
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            var status = bootstrapJobRepository.readStatus(jobId).orElseThrow();
            if (status.isTerminal()) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Bootstrap job did not reach a terminal state within 15s");
    }
}
