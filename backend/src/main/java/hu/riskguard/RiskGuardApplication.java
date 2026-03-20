package hu.riskguard;

import hu.riskguard.core.config.RiskGuardProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

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

	/**
	 * Canonical timezone for all business-level time logic (daily reset, session expiry).
	 * Hungarian company data + Hungarian users = Budapest timezone.
	 */
	public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Budapest");

	public static void main(String[] args) {
		SpringApplication.run(RiskGuardApplication.class, args);
	}

	/**
	 * System clock bean for injectable time — allows tests to use fixed clocks
	 * for deterministic time-dependent logic (e.g., guest session daily reset).
	 * Uses Budapest timezone for consistent daily boundary behavior across
	 * environments (dev=CET, prod=UTC container).
	 */
	@Bean
	Clock clock() {
		return Clock.system(BUSINESS_ZONE);
	}

}
