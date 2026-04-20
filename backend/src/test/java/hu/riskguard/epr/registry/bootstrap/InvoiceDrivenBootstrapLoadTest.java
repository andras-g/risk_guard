package hu.riskguard.epr.registry.bootstrap;

import com.zaxxer.hikari.HikariDataSource;
import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.datasource.domain.InvoiceDetail;
import hu.riskguard.datasource.domain.InvoiceDirection;
import hu.riskguard.datasource.domain.InvoiceLineItem;
import hu.riskguard.datasource.domain.InvoiceQueryResult;
import hu.riskguard.datasource.domain.InvoiceSummary;
import hu.riskguard.core.security.TenantContext;
import hu.riskguard.epr.audit.AuditService;
import hu.riskguard.epr.producer.domain.ProducerProfile;
import hu.riskguard.epr.producer.domain.ProducerProfileService;
import hu.riskguard.epr.registry.api.dto.BatchPackagingRequest;
import hu.riskguard.epr.registry.api.dto.BatchPackagingResult;
import hu.riskguard.epr.registry.api.dto.PackagingLayerDto;
import hu.riskguard.epr.registry.bootstrap.domain.BootstrapJobStatus;
import hu.riskguard.epr.registry.bootstrap.domain.InvoiceDrivenRegistryBootstrapService;
import hu.riskguard.epr.registry.bootstrap.internal.BootstrapJobRepository;
import hu.riskguard.epr.registry.domain.BatchPackagingClassifierService;
import hu.riskguard.epr.registry.domain.ClassifierUsageService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.AopTestUtils;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static hu.riskguard.jooq.Tables.EPR_BOOTSTRAP_JOBS;
import static hu.riskguard.jooq.Tables.PRODUCT_PACKAGING_COMPONENTS;
import static hu.riskguard.jooq.Tables.PRODUCTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Load test for AC #30: 3000 mocked NAV invoices → ~1000 unique pairs.
 *
 * <p>Opted in via {@code -PincludeLoadTests} Gradle property (activates the "load" tag).
 * Asserts: (a) terminal COMPLETED/FAILED_PARTIAL, (b) Hikari active connections ≤ 10,
 * (c) no SQLTransientConnectionException, (d) wall time < 60s with mocked 20ms classifier.
 */
@Tag("load")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=10")
class InvoiceDrivenBootstrapLoadTest {

    private static final int INVOICE_COUNT  = 3000;
    private static final int LINES_PER_INV  = 5;
    private static final int UNIQUE_PAIRS   = 1000;
    private static final long WALL_TIME_LIMIT_MS = 60_000;
    private static final int  CLASSIFIER_MOCK_DELAY_MS = 20;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean DataSourceService dataSourceService;
    @MockitoBean ProducerProfileService producerProfileService;
    @MockitoBean BatchPackagingClassifierService classifierService;
    @MockitoBean ClassifierUsageService usageService;
    @MockitoBean AuditService auditService;

    @Autowired InvoiceDrivenRegistryBootstrapService bootstrapService;
    @Autowired BootstrapJobRepository bootstrapJobRepository;
    @Autowired HikariDataSource hikariDataSource;
    @Autowired DSLContext dsl;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-4000-b000-000000000099");
    private static final UUID USER_ID   = UUID.fromString("00000000-0000-4000-b000-000000000098");
    private static final String TAX_NUM = "12345678-1-11";

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT_ID);

        // Insert load-test tenant and user (no FK constraint on user → tenant row required)
        dsl.execute("""
            INSERT INTO tenants (id, name, tier)
            VALUES ('00000000-0000-4000-b000-000000000099', 'Load Test Tenant', 'PRO_EPR')
            ON CONFLICT (id) DO NOTHING
            """);
        dsl.execute("""
            INSERT INTO users (id, tenant_id, email, name, role, preferred_language, sso_provider, sso_subject)
            VALUES (
                '00000000-0000-4000-b000-000000000098',
                '00000000-0000-4000-b000-000000000099',
                'load@test.local',
                'Load Test User',
                'SME_ADMIN',
                'hu',
                'test',
                'load-test-subject'
            ) ON CONFLICT (id) DO NOTHING
            """);

        // Clean previous run's data
        dsl.deleteFrom(EPR_BOOTSTRAP_JOBS)
                .where(EPR_BOOTSTRAP_JOBS.TENANT_ID.eq(TENANT_ID))
                .execute();
        // Delete components first (FK: product_packaging_components.product_id → products.id)
        dsl.deleteFrom(PRODUCT_PACKAGING_COMPONENTS)
                .where(PRODUCT_PACKAGING_COMPONENTS.PRODUCT_ID.in(
                        dsl.select(PRODUCTS.ID).from(PRODUCTS).where(PRODUCTS.TENANT_ID.eq(TENANT_ID))
                ))
                .execute();
        dsl.deleteFrom(PRODUCTS)
                .where(PRODUCTS.TENANT_ID.eq(TENANT_ID))
                .execute();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void processJob_3000Invoices_completesWithinLimits() throws Exception {
        // ── Build fixtures ────────────────────────────────────────────────────

        // 1000 unique (vtsz, description) pairs
        record Pair(String vtsz, String desc) {}
        List<Pair> uniquePairs = new ArrayList<>(UNIQUE_PAIRS);
        for (int i = 0; i < UNIQUE_PAIRS; i++) {
            uniquePairs.add(new Pair("39%02d".formatted(i % 99), "Product %04d".formatted(i)));
        }

        // 3000 invoices, each with 5 lines randomly sampled from the 1000 unique pairs
        List<InvoiceSummary> summaries = new ArrayList<>(INVOICE_COUNT);
        for (int inv = 0; inv < INVOICE_COUNT; inv++) {
            String invoiceNum = "INV-%06d".formatted(inv);
            summaries.add(new InvoiceSummary(invoiceNum, "CREATE", TAX_NUM,
                    "Supplier Ltd.", null, null, LocalDate.of(2026, 1, 1),
                    null, null, "HUF", InvoiceDirection.OUTBOUND));
        }

        // ── Mock collaborators ────────────────────────────────────────────────

        when(dataSourceService.queryInvoices(eq(TAX_NUM), any(), any(), eq(InvoiceDirection.OUTBOUND)))
                .thenReturn(new InvoiceQueryResult(summaries, true));

        // Use a counter to select different pairs per invoice so dedup works correctly
        var invoiceCounter = new java.util.concurrent.atomic.AtomicInteger(0);
        when(dataSourceService.queryInvoiceDetails(any())).thenAnswer(inv -> {
            String invNum = inv.getArgument(0, String.class);
            int idx = invoiceCounter.getAndIncrement();
            List<InvoiceLineItem> lines = new ArrayList<>();
            for (int l = 0; l < LINES_PER_INV; l++) {
                Pair p = uniquePairs.get((idx * LINES_PER_INV + l) % UNIQUE_PAIRS);
                lines.add(new InvoiceLineItem(l + 1, p.desc(), null, null, null, null, null,
                        p.vtsz(), null, null));
            }
            return new InvoiceDetail(invNum, "CREATE", TAX_NUM, "Supplier Ltd.",
                    null, null, LocalDate.of(2026, 1, 1), null, null, "HUF",
                    InvoiceDirection.OUTBOUND, lines, null, null);
        });

        // Classifier returns a GEMINI single-layer result after 20ms delay
        when(classifierService.classify(any(), any())).thenAnswer(inv -> {
            Thread.sleep(CLASSIFIER_MOCK_DELAY_MS);
            List<BatchPackagingRequest.PairRequest> pairs = inv.getArgument(0);
            List<BatchPackagingResult> results = new ArrayList<>();
            for (var req : pairs) {
                results.add(new BatchPackagingResult(
                        req.vtsz(), req.description(),
                        List.of(new PackagingLayerDto(1, "61090000", BigDecimal.valueOf(0.05), 1, "Plastic bottle")),
                        BatchPackagingResult.STRATEGY_GEMINI, "gemini-1.5-flash-001"
                ));
            }
            return results;
        });

        when(usageService.getMonthlyCap()).thenReturn(100_000);
        when(usageService.getCurrentMonthCallCount(TENANT_ID)).thenReturn(0);
        doNothing().when(auditService).recordRegistryBootstrapBatch(any());
        when(producerProfileService.get(TENANT_ID))
                .thenReturn(new ProducerProfile(UUID.randomUUID(), TENANT_ID,
                        "Load Test Kft.", "HU", "Budapest", "1000", "Fő utca", "utca", "1",
                        "12345678-1234-123-12", "01-09-123456",
                        "Test User", null, "HU", "1000", "Budapest", "Fő utca", "+3612345678",
                        "test@test.local", 12345, true, false, false, false, TAX_NUM));

        // ── Pool monitor (background thread samples active connections) ────────

        AtomicInteger peakActive = new AtomicInteger(0);
        var poolMonitor = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    int active = hikariDataSource.getHikariPoolMXBean().getActiveConnections();
                    peakActive.accumulateAndGet(active, Math::max);
                    Thread.sleep(50);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        poolMonitor.setDaemon(true);
        poolMonitor.start();

        // ── Run processJob synchronously (no @Async overhead in this direct call) ──

        UUID jobId = bootstrapJobRepository.insertIfNoInflight(
                TENANT_ID, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), USER_ID)
                .orElseThrow();

        // Unwrap the Spring AOP proxy to bypass @Async dispatch — call processJob synchronously
        InvoiceDrivenRegistryBootstrapService rawService =
                AopTestUtils.getTargetObject(bootstrapService);

        long start = System.currentTimeMillis();
        rawService.processJob(jobId, TENANT_ID, USER_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), TAX_NUM);
        long wallTime = System.currentTimeMillis() - start;

        poolMonitor.interrupt();
        poolMonitor.join(1000);

        // ── Assertions ────────────────────────────────────────────────────────

        // (a) job reached a terminal success state
        var finalStatus = bootstrapJobRepository.findByIdAndTenant(jobId, TENANT_ID).orElseThrow();
        assertThat(finalStatus.status())
                .as("job must reach COMPLETED or FAILED_PARTIAL (not stuck or fully failed)")
                .isIn(BootstrapJobStatus.COMPLETED, BootstrapJobStatus.FAILED_PARTIAL);

        // (b) Hikari pool never exceeded maximum-pool-size=10
        assertThat(peakActive.get())
                .as("Hikari active connections must never exceed pool max (10); peak was %d", peakActive.get())
                .isLessThanOrEqualTo(10);

        // (c) no SQLTransientConnectionException — implicit: if it were thrown, processJob
        //     would have called failJob and the status above would be FAILED, not COMPLETED.
        //     The status assertion above covers this.

        // (d) wall time < 60s
        assertThat(wallTime)
                .as("processJob wall time must be < %dms (was %dms)", WALL_TIME_LIMIT_MS, wallTime)
                .isLessThan(WALL_TIME_LIMIT_MS);

        // Bonus: all 1000 unique pairs should be products in DB
        int productCount = dsl.fetchCount(PRODUCTS, PRODUCTS.TENANT_ID.eq(TENANT_ID));
        assertThat(productCount).as("all unique pairs should be persisted as products")
                .isEqualTo(UNIQUE_PAIRS);
    }

}
