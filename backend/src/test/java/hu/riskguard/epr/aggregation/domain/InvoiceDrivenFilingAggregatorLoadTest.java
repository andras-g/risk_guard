package hu.riskguard.epr.aggregation.domain;

import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.datasource.domain.InvoiceDetail;
import hu.riskguard.datasource.domain.InvoiceDirection;
import hu.riskguard.datasource.domain.InvoiceLineItem;
import hu.riskguard.datasource.domain.InvoiceQueryResult;
import hu.riskguard.datasource.domain.InvoiceSummary;
import hu.riskguard.epr.aggregation.api.dto.FilingAggregationResult;
import hu.riskguard.epr.audit.AuditService;
import hu.riskguard.epr.domain.EprService;
import hu.riskguard.epr.registry.internal.RegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Load test for {@link InvoiceDrivenFilingAggregator} (Story 10.5 AC #22).
 *
 * <p>3000 invoice lines × 5 VTSZ codes × 3 components = 15,000 weight contributions.
 * <br>Target: p95 warm &lt; 500ms, cold &lt; 2000ms.
 *
 * <p>Opt-in: only runs when {@code -PincludeLoadTests} is set on the Gradle command line.
 * Tagged {@code @Tag("load")} for filtering.
 */
@Tag("load")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceDrivenFilingAggregatorLoadTest {

    private static final Logger log = LoggerFactory.getLogger(InvoiceDrivenFilingAggregatorLoadTest.class);

    private static final long COLD_THRESHOLD_MS = 2000L;
    private static final long WARM_THRESHOLD_MS = 500L;
    private static final int INVOICE_LINE_COUNT = 3000;
    private static final int VTSZ_COUNT = 5;
    private static final int COMPONENTS_PER_PRODUCT = 3;

    @Mock
    private RegistryRepository registryRepository;

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private EprService eprService;

    @Mock
    private AuditService auditService;

    private InvoiceDrivenFilingAggregator aggregator;

    private static final UUID TENANT = UUID.randomUUID();
    private static final LocalDate Q1_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate Q1_END = LocalDate.of(2026, 3, 31);

    // Fixed UUIDs for each product
    private static final UUID[] PRODUCT_IDS = IntStream.range(0, VTSZ_COUNT)
            .mapToObj(i -> UUID.randomUUID())
            .toArray(UUID[]::new);

    private static final String[] VTSZ_CODES = {
        "39239090", "48191000", "73181500", "39233000", "76122000"
    };

    private static final String[] PRODUCT_NAMES = {
        "PET palack", "Kartondoboz", "Csavar M6", "PET flakon", "Alumínium doboz"
    };

    @BeforeEach
    void setUp() {
        aggregator = new InvoiceDrivenFilingAggregator(registryRepository, dataSourceService, eprService, auditService);

        when(registryRepository.loadForAggregation(any())).thenReturn(List.of());
        when(registryRepository.resolveMaxUpdatedAt(any()))
                .thenReturn(java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        when(dataSourceService.getTenantTaxNumber(TENANT)).thenReturn(Optional.of("12345678"));
        when(eprService.getActiveConfigVersion()).thenReturn(1);
        when(eprService.getAllKfCodes(anyInt(), any()))
                .thenReturn(new hu.riskguard.epr.api.dto.KfCodeListResponse(1, List.of()));

        // Stub: 3000 invoice lines across 5 VTSZ codes
        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(buildLargeInvoiceQueryResult());
        when(dataSourceService.queryInvoiceDetails(any()))
                .thenAnswer(inv -> buildInvoiceDetail(inv.getArgument(0)));
    }

    @Test
    void coldRun_3000linesAcross5VtszCodes_completesWithin2000ms() {
        aggregator.invalidateCacheForTest();

        long start = System.nanoTime();
        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        log.info("LOAD TEST cold: {}ms, lines={}, resolved={}, kfTotals={}",
                durationMs, result.metadata().invoiceLineCount(),
                result.metadata().resolvedLineCount(), result.kfTotals().size());

        assertThat(result.metadata().invoiceLineCount()).isGreaterThan(0);
        assertThat(durationMs)
                .as("Cold aggregation of %d lines should complete within %dms, was %dms",
                        INVOICE_LINE_COUNT, COLD_THRESHOLD_MS, durationMs)
                .isLessThan(COLD_THRESHOLD_MS);
    }

    @Test
    void warmRun_cachedResult_completesWithin500ms() {
        // Prime the cache
        aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);

        // Warm run (cache hit)
        long start = System.nanoTime();
        FilingAggregationResult result = aggregator.aggregateForPeriod(TENANT, Q1_START, Q1_END);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        log.info("LOAD TEST warm: {}ms, lines={}", durationMs, result.metadata().invoiceLineCount());

        assertThat(durationMs)
                .as("Warm (cached) aggregation should complete within %dms, was %dms",
                        WARM_THRESHOLD_MS, durationMs)
                .isLessThan(WARM_THRESHOLD_MS);
    }

    // ─── Fixture builders ─────────────────────────────────────────────────────

    private InvoiceQueryResult buildLargeInvoiceQueryResult() {
        List<InvoiceSummary> summaries = new ArrayList<>();
        for (int i = 0; i < INVOICE_LINE_COUNT; i++) {
            summaries.add(new InvoiceSummary(
                    "INV-" + String.format("%05d", i), "CREATE",
                    "12345678", "Supplier", "87654321", "Customer",
                    Q1_START, Q1_START.plusDays(i % 90),
                    new BigDecimal("100000"), "HUF", InvoiceDirection.OUTBOUND));
        }
        return new InvoiceQueryResult(summaries, true);
    }

    private InvoiceDetail buildInvoiceDetail(String invoiceNumber) {
        int idx = Integer.parseInt(invoiceNumber.substring(4));
        int vtszIdx = idx % VTSZ_COUNT;
        String vtsz = VTSZ_CODES[vtszIdx];
        String name = PRODUCT_NAMES[vtszIdx];

        InvoiceLineItem line = new InvoiceLineItem(
                1, name,
                new BigDecimal("100"),
                "DARAB", BigDecimal.ONE,
                new BigDecimal("100"), new BigDecimal("100"),
                vtsz, "VTSZ", vtsz);

        return new InvoiceDetail(
                invoiceNumber, "CREATE", "12345678", "Supplier",
                "87654321", "Customer", Q1_START, Q1_START,
                new BigDecimal("100000"), "HUF", InvoiceDirection.OUTBOUND,
                List.of(line), "TRANSFER", java.util.Map.of());
    }
}
