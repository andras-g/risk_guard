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

    /** Cached HMAC-SHA key derived once at startup — single source of truth for signing and verification. */
    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        String secret = properties.getSecurity().getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            log.error("CRITICAL SECURITY VULNERABILITY: JWT secret is too weak or missing. It MUST be at least 32 bytes (256 bits).");
            throw new IllegalStateException("JWT secret too weak");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the cached signing key for JWT verification (e.g., by JwtDecoder).
     * This ensures a single source of truth — the same key used for signing is used for verification.
     */
    public SecretKey getSigningKey() {
        return signingKey;
    }

    public String createToken(String email, UUID userId, UUID homeTenantId, UUID activeTenantId, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + properties.getSecurity().getJwtExpirationMs());

        return Jwts.builder()
                .subject(email)
                .claim("user_id", userId.toString())
                .claim("home_tenant_id", homeTenantId.toString())
                .claim("active_tenant_id", activeTenantId.toString())
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }
}
