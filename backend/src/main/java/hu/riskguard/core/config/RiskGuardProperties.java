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
    private DataSource dataSource = new DataSource();
    private Security security = new Security();
    private AsyncIngestor asyncIngestor = new AsyncIngestor();
    private WatchlistMonitor watchlistMonitor = new WatchlistMonitor();

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
    public static class DataSource {
        private String mode = "demo";
        private int connectTimeoutMs = 8000;
        private int readTimeoutMs = 8000;
        private int globalDeadlineSeconds = 20;
    }

    @Data
    public static class Security {
        private String jwtSecret = "default_secret_must_be_overridden_in_production_32_chars_min";
        private long jwtExpirationMs = 3600000; // 1 hour
        private String frontendBaseUrl = "http://localhost:3000";
        private boolean cookieSecure = false; // Set to true behind TLS-terminating reverse proxy
    }

    /**
     * Configuration for the background async ingestor that refreshes partner data daily.
     * Currently runs sequentially on the Spring scheduler thread with inter-request delay.
     */
    @Data
    public static class AsyncIngestor {
        /** Spring cron expression — 6-field format: sec min hour day month dow. Default: 02:00 UTC daily. */
        private String cron = "0 0 2 * * ?";
        /** Delay in milliseconds between successive data source calls (rate limiting). */
        private long delayBetweenRequestsMs = 500;
        // TODO: threadPoolSize is reserved for future parallel execution via a dedicated
        //  ThreadPoolTaskExecutor bean. Currently unused — the ingestor runs sequentially
        //  on Spring's scheduler thread. Do not create a pool bean until workload demands it.
    }

    /**
     * Configuration for the background watchlist monitor that detects verdict status changes.
     * Runs after the AsyncIngestor (default: 04:00 UTC) to re-evaluate verdicts from fresh snapshots.
     * Sequential processing on the Spring scheduler thread with configurable inter-evaluation delay.
     */
    @Data
    public static class WatchlistMonitor {
        /** Spring cron expression — 6-field format: sec min hour day month dow. Default: 04:00 UTC daily. */
        private String cron = "0 0 4 * * ?";
        /** Delay in milliseconds between successive verdict evaluations (rate limiting). */
        private long delayBetweenEvaluationsMs = 200;
    }
}
