package hu.riskguard.datasource;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.security.TenantContext;
import hu.riskguard.datasource.api.dto.CompanyData;
import hu.riskguard.datasource.api.dto.ScrapedData;
import hu.riskguard.datasource.domain.CompanyDataPort;
import hu.riskguard.datasource.internal.CompanyDataAggregator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CompanyDataAggregator}.
 * Tests the parallel orchestration, result merging, and error handling.
 */
class CompanyDataAggregatorTest {

    private RiskGuardProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RiskGuardProperties();
        properties.getDataSource().setGlobalDeadlineSeconds(5);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("merge all successful adapters into single CompanyData")
    void mergeAllSuccessful() {
        CompanyDataPort adapter1 = createSuccessAdapter("adapter-a",
                Map.of("field1", "value1"), List.of("http://a/1"), true);
        CompanyDataPort adapter2 = createSuccessAdapter("adapter-b",
                Map.of("field2", "value2"), List.of("http://b/1"), true);

        var aggregator = new CompanyDataAggregator(List.of(adapter1, adapter2), properties);
        CompanyData result = aggregator.aggregate("12345678");

        assertThat(result.snapshotData()).containsKeys("adapter-a", "adapter-b");
        assertThat(result.adapterResults()).containsKeys("adapter-a", "adapter-b");
        assertThat(result.sourceUrls()).containsExactlyInAnyOrder("http://a/1", "http://b/1");
        assertThat(result.adapterResults().get("adapter-a").available()).isTrue();
        assertThat(result.adapterResults().get("adapter-b").available()).isTrue();
        assertThat(result.domFingerprintHash()).isNotNull();
    }

    @Test
    @DisplayName("partial failure includes available results and marks failed as SOURCE_UNAVAILABLE")
    void partialFailure() {
        CompanyDataPort successAdapter = createSuccessAdapter("good",
                Map.of("data", "ok"), List.of("http://good/1"), true);
        CompanyDataPort failingAdapter = createFailingAdapter("bad");

        var aggregator = new CompanyDataAggregator(List.of(successAdapter, failingAdapter), properties);
        CompanyData result = aggregator.aggregate("12345678");

        assertThat(result.adapterResults().get("good").available()).isTrue();
        assertThat(result.adapterResults().get("bad").available()).isFalse();
    }

    @Test
    @DisplayName("all failing adapters produces all-unavailable result")
    void allFailing() {
        CompanyDataPort fail1 = createFailingAdapter("fail-a");
        CompanyDataPort fail2 = createFailingAdapter("fail-b");

        var aggregator = new CompanyDataAggregator(List.of(fail1, fail2), properties);
        CompanyData result = aggregator.aggregate("12345678");

        assertThat(result.adapterResults()).allSatisfy((name, data) ->
                assertThat(data.available()).isFalse());
    }

    @Test
    @DisplayName("global deadline timeout marks timed-out adapters as unavailable")
    void globalDeadlineTimeout() {
        properties.getDataSource().setGlobalDeadlineSeconds(2);

        CompanyDataPort slowAdapter = new CompanyDataPort() {
            @Override
            public ScrapedData fetch(String taxNumber) {
                try { Thread.sleep(30_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return new ScrapedData("slow", Map.of(), List.of(), true, null);
            }
            @Override public String adapterName() { return "slow"; }
            @Override public Set<String> requiredFields() { return Set.of(); }
        };

        var aggregator = new CompanyDataAggregator(List.of(slowAdapter), properties);
        CompanyData result = aggregator.aggregate("12345678");

        // Slow adapter should be timed out and marked unavailable
        assertThat(result.adapterResults()).containsKey("slow");
        assertThat(result.adapterResults().get("slow").available()).isFalse();
    }

    @Test
    @DisplayName("tenant context is propagated to virtual threads")
    void tenantContextPropagation() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenant(tenantId);

        final UUID[] capturedTenantId = {null};
        CompanyDataPort capturingAdapter = new CompanyDataPort() {
            @Override
            public ScrapedData fetch(String taxNumber) {
                capturedTenantId[0] = TenantContext.getCurrentTenant();
                return new ScrapedData("capturing", Map.of(), List.of(), true, null);
            }
            @Override public String adapterName() { return "capturing"; }
            @Override public Set<String> requiredFields() { return Set.of(); }
        };

        var aggregator = new CompanyDataAggregator(List.of(capturingAdapter), properties);
        aggregator.aggregate("12345678");

        assertThat(capturedTenantId[0]).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("adapter returning available=false is marked as SOURCE_UNAVAILABLE in snapshot data")
    void adapterReturnUnavailable() {
        CompanyDataPort unavailableAdapter = createSuccessAdapter("unavail",
                Map.of(), List.of(), false);

        var aggregator = new CompanyDataAggregator(List.of(unavailableAdapter), properties);
        CompanyData result = aggregator.aggregate("12345678");

        assertThat(result.adapterResults().get("unavail").available()).isFalse();
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshotEntry = (Map<String, Object>) result.snapshotData().get("unavail");
        assertThat(snapshotEntry).containsEntry("status", "SOURCE_UNAVAILABLE");
    }

    // --- Helper methods ---

    private static CompanyDataPort createSuccessAdapter(String name, Map<String, Object> data,
                                                        List<String> urls, boolean available) {
        return new CompanyDataPort() {
            @Override
            public ScrapedData fetch(String taxNumber) {
                return new ScrapedData(name, data, urls, available,
                        available ? null : "test unavailable");
            }
            @Override public String adapterName() { return name; }
            @Override public Set<String> requiredFields() { return Set.of(); }
        };
    }

    private static CompanyDataPort createFailingAdapter(String name) {
        return new CompanyDataPort() {
            @Override
            public ScrapedData fetch(String taxNumber) {
                throw new RuntimeException("Test failure for " + name);
            }
            @Override public String adapterName() { return name; }
            @Override public Set<String> requiredFields() { return Set.of(); }
        };
    }
}
