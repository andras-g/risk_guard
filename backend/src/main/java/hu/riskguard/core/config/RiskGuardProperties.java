package hu.riskguard.core.config;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "risk-guard")
public class RiskGuardProperties {
    private Freshness freshness = new Freshness();
    private Guest guest = new Guest();
    private List<String> tiers = new ArrayList<>();
    private RateLimits rateLimits = new RateLimits();
    private Security security = new Security();

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
    }
}
