package hu.riskguard.datasource.internal;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.datasource.domain.CompanyDataPort;
import hu.riskguard.datasource.internal.adapters.demo.DemoCompanyDataAdapter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Configuration class that conditionally creates data source adapter beans
 * based on the {@code riskguard.data-source.mode} property.
 *
 * <p>Supported modes:
 * <ul>
 *   <li>{@code demo} — Registers {@link DemoCompanyDataAdapter} (in-memory fixtures)</li>
 *   <li>{@code test}/{@code live} — Requires NAV adapter beans (not yet implemented)</li>
 * </ul>
 */
@Configuration
public class DataSourceModeConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceModeConfig.class);

    @Bean
    // Note: property name "riskguard.data-source.mode" is resolved via Spring Boot relaxed binding
    // from the YAML key "risk-guard.data-source.mode" (prefix "risk-guard" → bound to RiskGuardProperties).
    // The two forms are equivalent; "riskguard" is the canonical (kebab-to-flat) form.
    @ConditionalOnProperty(name = "riskguard.data-source.mode", havingValue = "demo")
    public CompanyDataPort demoCompanyDataAdapter() {
        log.info("Data source mode: demo — registering DemoCompanyDataAdapter with in-memory fixtures");
        return new DemoCompanyDataAdapter();
    }

    /**
     * Startup validator that ensures non-demo modes have at least one non-demo
     * {@link CompanyDataPort} bean registered. Fails fast with a descriptive error
     * if {@code test} or {@code live} mode is configured but no NAV adapter exists.
     *
     * <p>Declared as {@code @Component} (not {@code @Configuration}) to prevent Spring from
     * treating it as a configuration class that could trigger additional component scanning.
     */
    @Component
    public static class DataSourceModeValidator {

        private final RiskGuardProperties properties;
        private final ApplicationContext applicationContext;

        public DataSourceModeValidator(RiskGuardProperties properties, ApplicationContext applicationContext) {
            this.properties = properties;
            this.applicationContext = applicationContext;
        }

        @PostConstruct
        public void validateMode() {
            String mode = properties.getDataSource().getMode();
            if ("test".equals(mode) || "live".equals(mode)) {
                var ports = applicationContext.getBeansOfType(CompanyDataPort.class);
                boolean hasNonDemoAdapter = ports.values().stream()
                        .anyMatch(p -> !"demo".equals(p.adapterName()));
                if (!hasNonDemoAdapter) {
                    throw new IllegalStateException(
                            "Data source mode '" + mode + "' requires NAV adapter beans which are not yet implemented. "
                                    + "Use 'demo' mode or implement NavOnlineSzamlaAdapter.");
                }
            }
        }
    }
}
