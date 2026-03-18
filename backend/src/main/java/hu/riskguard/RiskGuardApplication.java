package hu.riskguard;

import hu.riskguard.core.config.RiskGuardProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application entry point for RiskGuard.
 *
 * <p>{@link EnableConfigurationProperties} registers {@link RiskGuardProperties} without
 * the {@code @Component} anti-pattern — ensuring property binding completes before
 * {@code @PostConstruct} hooks execute, as required by Spring Boot 4.
 *
 * <p>{@link EnableScheduling} activates the Spring scheduler for {@code @Scheduled} methods
 * (used by {@code AsyncIngestor} for daily background data refresh — Story 3.5).
 */
@SpringBootApplication
@EnableConfigurationProperties(RiskGuardProperties.class)
@EnableScheduling
public class RiskGuardApplication {

	public static void main(String[] args) {
		SpringApplication.run(RiskGuardApplication.class, args);
	}

}
