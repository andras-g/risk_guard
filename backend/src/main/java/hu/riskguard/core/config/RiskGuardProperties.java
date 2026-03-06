package hu.riskguard.core.config;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

@Data
@Component
@ConfigurationProperties(prefix = "risk-guard")
public class RiskGuardProperties {
    private Freshness freshness = new Freshness();
    private Guest guest = new Guest();
    private Identity identity = new Identity();
    private RateLimits rateLimits = new RateLimits();
    private Security security = new Security();

    @PostConstruct
    public void init() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(new ClassPathResource("risk-guard-tokens.json").getInputStream());
            if (node.has("cookieName")) {
                this.identity.setCookieName(node.get("cookieName").asText());
            }
        } catch (Exception e) {
            // fallback
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
    }
}
