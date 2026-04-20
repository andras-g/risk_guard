package hu.riskguard.epr.aggregation.domain;

import hu.riskguard.epr.aggregation.api.dto.FilingAggregationResult;
import hu.riskguard.epr.audit.AuditService;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Integration test for {@link InvoiceDrivenFilingAggregator} (Story 10.5 AC #18).
 *
 * <p>Uses the Demo tenant seeded by Flyway ({@code R__demo_data.sql}).
 * The Demo tenant has 3 active products with packaging components matching the
 * VTSZ codes returned by {@code DemoInvoiceFixtures} (73181500 screws, 39233000 PET,
 * 48191000 cardboard).
 *
 * <p>Verifies:
 * <ul>
 *   <li>Full DB-to-aggregation pipeline: invoice lines → registry lookup → weight totals
 *   <li>Caffeine cache hit: second call with identical params does not re-invoke audit
 * </ul>
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class InvoiceDrivenFilingAggregatorIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AuditService auditService;

    @Autowired
    private InvoiceDrivenFilingAggregator aggregator;

    /** Demo Felhasználó — has products seeded by R__demo_data.sql */
    private static final UUID DEMO_TENANT = UUID.fromString("00000000-0000-4000-b000-000000000001");

    /** DemoInvoiceFixtures generates invoices for the previous quarter relative to today */
    private static final LocalDate Q1_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate Q1_END = LocalDate.of(2026, 3, 31);

    @Test
    void aggregateForPeriod_demoTenantQ1_returnsSomeKfTotals() {
        FilingAggregationResult result = aggregator.aggregateForPeriod(DEMO_TENANT, Q1_START, Q1_END);

        assertThat(result).isNotNull();
        assertThat(result.metadata().invoiceLineCount()).isGreaterThan(0);
        // At least the Csavar (73181500) + PET (39233000) lines should resolve to kfTotals
        assertThat(result.kfTotals())
                .as("Demo tenant registry should produce at least one KF total from matched products")
                .isNotEmpty();
        // KfCodeTotal weights must be positive and fees non-negative
        result.kfTotals().forEach(t -> {
            assertThat(t.totalWeightKg().doubleValue()).isGreaterThan(0);
            assertThat(t.totalFeeHuf().doubleValue()).isGreaterThanOrEqualTo(0);
        });
    }

    @Test
    void aggregateForPeriod_resultContainsMetadataWithCorrectPeriod() {
        FilingAggregationResult result = aggregator.aggregateForPeriod(DEMO_TENANT, Q1_START, Q1_END);

        assertThat(result.metadata().periodStart()).isEqualTo(Q1_START);
        assertThat(result.metadata().periodEnd()).isEqualTo(Q1_END);
        assertThat(result.metadata().activeConfigVersion()).isGreaterThan(0);
        assertThat(result.metadata().aggregationDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void aggregateForPeriod_cacheHit_auditCalledOnlyOnce() {
        // Clear any previous cache state by using a unique period window
        LocalDate start = LocalDate.of(2026, 2, 1);
        LocalDate end = LocalDate.of(2026, 2, 28);

        aggregator.invalidateCacheForTest();

        FilingAggregationResult first = aggregator.aggregateForPeriod(DEMO_TENANT, start, end);
        FilingAggregationResult second = aggregator.aggregateForPeriod(DEMO_TENANT, start, end);

        // Both calls return the same result (cache hit on second)
        assertThat(second.metadata().invoiceLineCount())
                .isEqualTo(first.metadata().invoiceLineCount());

        // AuditService should have been called exactly once (second call was a cache hit)
        verify(auditService, times(1)).recordAggregationRun(
                any(UUID.class), any(LocalDate.class), any(LocalDate.class),
                anyLong(), anyInt(), anyInt());
    }

    @Test
    void aggregateForPeriod_soldProductsMatchInvoiceVtsz() {
        FilingAggregationResult result = aggregator.aggregateForPeriod(DEMO_TENANT, Q1_START, Q1_END);

        // soldProducts should contain entries for matched products
        assertThat(result.soldProducts()).isNotEmpty();
        result.soldProducts().forEach(sp -> {
            assertThat(sp.vtsz()).isNotBlank();
            assertThat(sp.totalQuantity().doubleValue()).isGreaterThan(0);
            assertThat(sp.unitOfMeasure()).isEqualToIgnoringCase("DARAB");
        });
    }
}
