package hu.riskguard.core.security;

import hu.riskguard.core.config.RiskGuardProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenProvider {

    private final RiskGuardProperties properties;

    @PostConstruct
    public void validateSecret() {
        String secret = properties.getSecurity().getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            log.error("CRITICAL SECURITY VULNERABILITY: JWT secret is too weak or missing. It MUST be at least 32 bytes (256 bits).");
            throw new IllegalStateException("JWT secret too weak");
        }
    }

    public String createToken(String email, UUID homeTenantId, UUID activeTenantId) {
        SecretKey key = Keys.hmacShaKeyFor(properties.getSecurity().getJwtSecret().getBytes(StandardCharsets.UTF_8));
        
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + properties.getSecurity().getJwtExpirationMs());

        return Jwts.builder()
                .subject(email)
                .claim("home_tenant_id", homeTenantId.toString())
                .claim("active_tenant_id", activeTenantId.toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }
}
