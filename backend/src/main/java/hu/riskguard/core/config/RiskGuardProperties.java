package hu.riskguard.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "risk-guard")
public class RiskGuardProperties {
    private int freshThresholdHours = 6;
    private int staleThresholdHours = 24;
    private int unavailableThresholdHours = 48;
    
    private Security security = new Security();

    @Data
    public static class Security {
        private String jwtSecret = "default_secret_must_be_overridden_in_production_32_chars_min";
        private long jwtExpirationMs = 3600000; // 1 hour
    }
}
