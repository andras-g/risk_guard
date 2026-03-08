package hu.riskguard.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

/**
 * Typed configuration properties for the risk-guard application.
 *
 * <p>Registered via {@code @EnableConfigurationProperties} on {@link hu.riskguard.RiskGuardApplication}
 * to avoid the {@code @Component} + {@code @ConfigurationProperties} anti-pattern in Spring Boot 4,
 * where {@code @PostConstruct} may fire before property binding is complete.
 */
@Data
@ConfigurationProperties(prefix = "risk-guard")
public class RiskGuardProperties {

    private static final Logger log = LoggerFactory.getLogger(RiskGuardProperties.class);

    private Freshness freshness = new Freshness();
    private Guest guest = new Guest();
    private Identity identity = new Identity();
    private RateLimits rateLimits = new RateLimits();
    private Security security = new Security();

    @PostConstruct
    public void init() {
        loadTokensConfig();
        validateSecurityConfig();
    }

    private void loadTokensConfig() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(new ClassPathResource("risk-guard-tokens.json").getInputStream());
            if (node.has("cookieName")) {
                this.identity.setCookieName(node.get("cookieName").asText());
            }
        } catch (Exception e) {
            log.warn("Failed to load risk-guard-tokens.json from classpath. Using default configuration. Error: {}", e.getMessage());
        }
    }

    /**
     * Validates security configuration at startup.
     * NOTE: JWT secret strength validation (min 32 bytes / 256 bits) is performed by
     * {@link hu.riskguard.core.security.TokenProvider#validateSecret()} to avoid
     * duplicate checks with inconsistent thresholds (char vs byte length).
     * This method only warns about known default/development secret values.
     */
    private void validateSecurityConfig() {
        String secret = security.getJwtSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret must not be null or blank. "
                    + "Set the JWT_SECRET environment variable.");
        }
        // Reject known default values in non-dev profiles
        if (secret.startsWith("default_secret") || secret.startsWith("local-dev-secret")) {
            log.warn("SECURITY WARNING: JWT secret appears to be a default/development value. "
                    + "Set JWT_SECRET environment variable to a strong random secret in production.");
        }
    }

    @Data
    public static class Identity {
        private String defaultTier = "ALAP";
        private String defaultLanguage = "hu";
        private String defaultUserRole = "SME_ADMIN";
        private String cookieName = "auth_token";
    }

    @Data
    public static class Freshness {
        private int freshThresholdHours = 6;
        private int staleThresholdHours = 24;
        private int unavailableThresholdHours = 48;
    }

    @Data
    public static class Guest {
        private int maxCompanies = 10;
        private int maxDailyChecks = 3;
        private int captchaAfterChecks = 3;
    }

    @Data
    public static class RateLimits {
        private int searchesPerMinutePerTenant = 30;
        private int maxAlertsPerDayPerTenant = 10;
    }

    @Data
    public static class Security {
        private String jwtSecret = "default_secret_must_be_overridden_in_production_32_chars_min";
        private long jwtExpirationMs = 3600000; // 1 hour
        private String frontendBaseUrl = "http://localhost:3000";
        private boolean cookieSecure = false; // Set to true behind TLS-terminating reverse proxy
    }
}
