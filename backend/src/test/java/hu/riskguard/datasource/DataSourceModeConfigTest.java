package hu.riskguard.datasource;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.datasource.api.dto.ScrapedData;
import hu.riskguard.datasource.domain.CompanyDataPort;
import hu.riskguard.datasource.internal.DataSourceModeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DataSourceModeConfig} — verifies mode-based adapter registration
 * and fail-fast behavior for unconfigured modes.
 */
class DataSourceModeConfigTest {

    @Nested
    @DisplayName("Demo mode")
    @SpringBootTest(classes = {DataSourceModeConfig.class, RiskGuardProperties.class})
    @TestPropertySource(properties = {
            "riskguard.data-source.mode=demo",
            "risk-guard.security.jwt-secret=test-secret-32-chars-long-at-least-12",
    })
    class DemoMode {

        @Autowired
        private List<CompanyDataPort> adapters;

        @Test
        @DisplayName("demo mode registers exactly one DemoCompanyDataAdapter — no DB, no full context")
        void demoModeRegistersDemoAdapter() {
            assertThat(adapters).hasSize(1);
            assertThat(adapters.getFirst().adapterName()).isEqualTo("demo");
        }
    }

    @Nested
    @DisplayName("Test mode (fail-fast)")
    class TestMode {

        @Test
        @DisplayName("test mode without NAV beans fails fast with IllegalStateException")
        void testModeFailsFastWithoutNavBeans() {
            // Set up properties with mode=test
            var properties = new RiskGuardProperties();
            properties.getDataSource().setMode("test");

            // Create an ApplicationContext with zero CompanyDataPort beans
            var ctx = new StaticApplicationContext();
            ctx.refresh();

            // Instantiate the validator and invoke validateMode() directly
            var validator = new DataSourceModeConfig.DataSourceModeValidator(properties, ctx);

            assertThatThrownBy(validator::validateMode)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("test")
                    .hasMessageContaining("requires NAV adapter beans");
        }

        @Test
        @DisplayName("test mode with only demo adapter still fails fast")
        void testModeWithOnlyDemoAdapterFailsFast() {
            var properties = new RiskGuardProperties();
            properties.getDataSource().setMode("test");

            // Register only a demo adapter — should still fail since mode=test needs non-demo adapters
            var ctx = new StaticApplicationContext();
            ctx.registerBean("demoAdapter", CompanyDataPort.class, () -> new CompanyDataPort() {
                @Override public ScrapedData fetch(String taxNumber) { return null; }
                @Override public String adapterName() { return "demo"; }
                @Override public Set<String> requiredFields() { return Set.of(); }
            });
            ctx.refresh();

            var validator = new DataSourceModeConfig.DataSourceModeValidator(properties, ctx);

            assertThatThrownBy(validator::validateMode)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("test")
                    .hasMessageContaining("requires NAV adapter beans");
        }
    }

    @Nested
    @DisplayName("Live mode (fail-fast)")
    class LiveMode {

        @Test
        @DisplayName("live mode without NAV beans fails fast with IllegalStateException")
        void liveModeFailsFastWithoutNavBeans() {
            var properties = new RiskGuardProperties();
            properties.getDataSource().setMode("live");

            var ctx = new StaticApplicationContext();
            ctx.refresh();

            var validator = new DataSourceModeConfig.DataSourceModeValidator(properties, ctx);

            assertThatThrownBy(validator::validateMode)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("live")
                    .hasMessageContaining("requires NAV adapter beans");
        }
    }

    @Nested
    @DisplayName("Demo mode does not trigger fail-fast")
    class DemoModeNoFailFast {

        @Test
        @DisplayName("demo mode validation passes without any adapters registered")
        void demoModeDoesNotFailFast() {
            var properties = new RiskGuardProperties();
            properties.getDataSource().setMode("demo");

            var ctx = new StaticApplicationContext();
            ctx.refresh();

            var validator = new DataSourceModeConfig.DataSourceModeValidator(properties, ctx);

            // Should not throw — demo mode has no requirement for NAV adapter beans
            validator.validateMode();
        }
    }
}
