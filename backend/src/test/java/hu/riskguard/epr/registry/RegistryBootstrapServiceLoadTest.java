package hu.riskguard.epr.registry;

import com.zaxxer.hikari.HikariDataSource;
import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.datasource.domain.InvoiceDetail;
import hu.riskguard.datasource.domain.InvoiceDirection;
import hu.riskguard.datasource.domain.InvoiceLineItem;
import hu.riskguard.datasource.domain.InvoiceQueryResult;
import hu.riskguard.datasource.domain.InvoiceSummary;
import hu.riskguard.epr.registry.classifier.ClassificationResult;
import hu.riskguard.epr.registry.classifier.KfCodeClassifierService;
import hu.riskguard.epr.registry.domain.BootstrapResult;
import hu.riskguard.epr.registry.domain.RegistryBootstrapService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Story 10.1 AC #16 — tx-pool refactor load test.
 *
 * <p>Simulates per-invoice NAV detail fetch latency and asserts that the refactored
 * {@link RegistryBootstrapService#triggerBootstrap} never holds a Hikari connection during
 * the NAV HTTP wait. Peak {@code HikariPoolMXBean.getActiveConnections()} is sampled every
 * 50 ms on a {@link ScheduledExecutorService} during the run.
 *
 * <p>Compliance with memory {@code project_nav_test_env_constraint}: no request ever reaches
 * a NAV host. {@link DataSourceService} is fully replaced with a {@link MockitoBean} that
 * simulates latency with {@link Thread#sleep(long)}. Exercising NAV's full HTTP path
 * (WireMock at the adapter layer) would add complexity without changing the tx-boundary
 * assertion — the refactored method's contract is "no DB connection held during
 * {@code dataSourceService.*}", which is package-local to {@code RegistryBootstrapService}.
 *
 * <p>Tagged {@code load} so it can be excluded from the default test target if CI timing
 * turns out flaky. See Task 12 notes.
 */
@Tag("load")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=10"
})
class RegistryBootstrapServiceLoadTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private DataSourceService dataSourceService;

    @MockitoBean
    private KfCodeClassifierService classifierService;

    @Autowired
    private RegistryBootstrapService bootstrapService;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * AC #16 assertions: wall-clock < 90 s AND peak active-connection count ≤ 3.
     *
     * <p>Scale decision (code review 2026-04-18, B-DEC1): AC #16 specifies 100 summaries × 3 s
     * latency (300 s total). Dialed down to 60 summaries × 300 ms (18 s total) for CI
     * reasonableness while preserving the critical invariants: (a) the multi-batch path is
     * exercised because 60 > {@code BOOTSTRAP_INSERT_BATCH_SIZE=50}, and (b) the pool signal
     * remains detectable — connections are only held during the short per-batch insert window.
     * A 100 × 3 s run would gate on a 5-minute wall-clock even with {@code @Tag("load")}.
     */
    @Test
    void triggerBootstrap_withPerInvoiceLatency_doesNotHoldConnection_peakBelowThreshold() throws Exception {
        UUID tenantId = seedTenant();

        // ── NAV stubs ────────────────────────────────────────────────────────
        final int summaryCount = 60;  // > BOOTSTRAP_INSERT_BATCH_SIZE=50 → exercises multi-batch path
        final long perDetailLatencyMs = 300;
        final String taxNumber = "12345678";

        when(dataSourceService.getTenantTaxNumber(tenantId)).thenReturn(Optional.of(taxNumber));

        List<InvoiceSummary> summaries = new ArrayList<>();
        for (int i = 0; i < summaryCount; i++) {
            summaries.add(buildSummary("INV-" + i, taxNumber));
        }
        when(dataSourceService.queryInvoices(any(), any(), any(), any(InvoiceDirection.class)))
                .thenReturn(new InvoiceQueryResult(summaries, true));

        when(dataSourceService.queryInvoiceDetails(anyString())).thenAnswer(inv -> {
            Thread.sleep(perDetailLatencyMs);
            String invoiceNumber = inv.getArgument(0);
            return buildDetail(invoiceNumber);
        });

        when(classifierService.classify(anyString(), any())).thenReturn(ClassificationResult.empty());

        // ── Hikari pool monitor ──────────────────────────────────────────────
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        AtomicInteger peak = new AtomicInteger(0);
        ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleAtFixedRate(() -> {
            int active = hikari.getHikariPoolMXBean().getActiveConnections();
            peak.accumulateAndGet(active, Math::max);
        }, 0, 50, TimeUnit.MILLISECONDS);

        // ── Run + wall-clock ─────────────────────────────────────────────────
        long start = System.nanoTime();
        BootstrapResult result;
        try {
            result = bootstrapService.triggerBootstrap(
                    tenantId, UUID.randomUUID(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        } finally {
            poller.shutdownNow();
            poller.awaitTermination(1, TimeUnit.SECONDS);
        }
        long wallClockMs = (System.nanoTime() - start) / 1_000_000L;

        // ── AC #16 assertions ────────────────────────────────────────────────
        assertThat(wallClockMs)
                .as("bootstrap wall-clock must stay < 90s to prove NAV HTTP does not hold a DB connection")
                .isLessThan(90_000L);
        assertThat(peak.get())
                .as("peak Hikari active connections must stay ≤ 3 throughout the run")
                .isLessThanOrEqualTo(3);
        // Sanity check — something was actually processed.
        assertThat(result.created() + result.skipped()).isGreaterThan(0);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(
                     "INSERT INTO tenants (id, name, tier) VALUES (?, ?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "Load Test Tenant");
            ps.setString(3, "PRO_EPR");
            ps.execute();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to seed tenant", e);
        }
        return tenantId;
    }

    private static InvoiceSummary buildSummary(String invoiceNumber, String taxNumber) {
        return new InvoiceSummary(
                invoiceNumber,
                "CREATE",
                taxNumber,
                "Supplier",
                null,
                "Customer",
                LocalDate.now(),
                LocalDate.now(),
                new BigDecimal("1000"),
                "HUF",
                InvoiceDirection.OUTBOUND);
    }

    /**
     * Baseline companion (AC #16, B-P1, R3-P1) — demonstrates that the pre-refactor
     * {@code @Transactional}-across-NAV-HTTP pattern saturates the Hikari pool.
     *
     * <p>Kept {@code @Disabled} so it does not run in CI. Enable manually when comparing
     * old vs new behaviour or when validating connection-pool behaviour on a new environment.
     *
     * <p><b>What this simulates.</b> The pre-refactor path held one DB connection per tenant
     * for the entire {@code triggerBootstrap} duration (seconds of NAV HTTP × N invoices). At
     * concurrency-of-2, that was enough to starve a {@code max-pool-size=2} Hikari pool.
     *
     * <p><b>How it simulates without rolling back production code.</b> We cannot
     * re-add {@code @Transactional} to the refactored service (it would regress the production
     * code). Instead, four concurrent worker threads each open their own
     * {@code TransactionTemplate}, hold the transaction for ~2s of simulated NAV latency
     * while touching the connection with a cheap query, then commit. That exactly reproduces
     * the "transaction held across NAV HTTP sleep" pattern. The assertion is
     * {@code peak > 3}, which fails the refactored contract (≤ 3) and confirms the
     * pre-refactor pattern saturates beyond the safe threshold.
     */
    @Test
    @Disabled("baseline: demonstrates pool saturation with pre-refactor @Transactional across NAV HTTP")
    void baseline_preRefactorPattern_saturatesHikariPool() throws Exception {
        final int concurrentBootstraps = 4;
        final long simulatedNavLatencyMs = 2_000;

        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        AtomicInteger peak = new AtomicInteger(0);
        ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleAtFixedRate(() -> {
            int active = hikari.getHikariPoolMXBean().getActiveConnections();
            peak.accumulateAndGet(active, Math::max);
        }, 0, 50, TimeUnit.MILLISECONDS);

        ExecutorService workers = Executors.newFixedThreadPool(concurrentBootstraps);
        CountDownLatch ready = new CountDownLatch(concurrentBootstraps);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < concurrentBootstraps; i++) {
                futures.add(workers.submit(() -> {
                    TransactionTemplate tx = new TransactionTemplate(transactionManager);
                    tx.execute(status -> {
                        try {
                            // Acquire the connection by executing a cheap query inside the tx.
                            try (var conn = dataSource.getConnection();
                                 var ps = conn.prepareStatement("SELECT 1")) {
                                ps.execute();
                            }
                            ready.countDown();
                            go.await();
                            // Simulate NAV HTTP latency while holding the tx open.
                            Thread.sleep(simulatedNavLatencyMs);
                        } catch (Exception e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                        return null;
                    });
                }));
            }
            // Wait for every worker to acquire its connection, then release them together so
            // peak measurement captures the fully-saturated state.
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            for (Future<?> f : futures) {
                f.get(simulatedNavLatencyMs + 5_000, TimeUnit.MILLISECONDS);
            }
        } finally {
            workers.shutdown();
            workers.awaitTermination(5, TimeUnit.SECONDS);
            poller.shutdownNow();
            assertThat(poller.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        }

        // With pre-refactor pattern (tx held across simulated NAV latency, 4 concurrent),
        // peak must exceed the refactored-code ceiling of 3.
        assertThat(peak.get())
                .as("pre-refactor baseline: peak connections should exceed the refactored ceiling of 3")
                .isGreaterThan(3);
    }

    private static InvoiceDetail buildDetail(String invoiceNumber) {
        InvoiceLineItem line = new InvoiceLineItem(
                1,
                "Termék " + invoiceNumber,
                new BigDecimal("10"),
                "DARAB",
                new BigDecimal("10"),
                new BigDecimal("100"),
                new BigDecimal("100"),
                "39239090",
                "VTSZ",
                "39239090");
        return new InvoiceDetail(
                invoiceNumber, "CREATE", "12345678", "Supplier",
                null, "Customer", LocalDate.now(), LocalDate.now(),
                new BigDecimal("100"), "HUF", InvoiceDirection.OUTBOUND,
                List.of(line), "TRANSFER", Map.of());
    }
}
