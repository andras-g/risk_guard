package hu.riskguard.core.security;

import hu.riskguard.core.config.RiskGuardProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TokenProvider {

    private final RiskGuardProperties properties;

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
