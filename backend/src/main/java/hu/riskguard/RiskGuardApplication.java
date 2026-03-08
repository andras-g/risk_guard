package hu.riskguard;

import hu.riskguard.core.config.RiskGuardProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main application entry point for RiskGuard.
 *
 * <p>{@link EnableConfigurationProperties} registers {@link RiskGuardProperties} without
 * the {@code @Component} anti-pattern — ensuring property binding completes before
 * {@code @PostConstruct} hooks execute, as required by Spring Boot 4.
 */
@SpringBootApplication
@EnableConfigurationProperties(RiskGuardProperties.class)
public class RiskGuardApplication {

	public static void main(String[] args) {
		SpringApplication.run(RiskGuardApplication.class, args);
	}

}
