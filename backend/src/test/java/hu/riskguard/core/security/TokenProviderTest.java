package hu.riskguard.core.security;

import hu.riskguard.core.config.RiskGuardProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenProviderTest {

    private static final String TEST_SECRET = "dummy_secret_for_testing_32_chars_long_long";

    private TokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        RiskGuardProperties properties = new RiskGuardProperties();
        properties.getSecurity().setJwtSecret(TEST_SECRET);
        properties.getSecurity().setJwtExpirationMs(3600000L);
        tokenProvider = new TokenProvider(properties);
        // Simulate Spring @PostConstruct lifecycle — validates secret and caches signing key
        tokenProvider.init();
    }

    @Test
    void shouldIncludeRoleClaimInToken() {
        // Given
        String email = "user@test.com";
        UUID homeTenantId = UUID.randomUUID();
        UUID activeTenantId = UUID.randomUUID();
        String role = "SME_ADMIN";

        // When
        String token = tokenProvider.createToken(email, homeTenantId, activeTenantId, role);

        // Then — verify using the same cached key exposed by getSigningKey()
        Claims claims = Jwts.parser().verifyWith(tokenProvider.getSigningKey()).build()
                .parseSignedClaims(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo(email);
        assertThat(claims.get("home_tenant_id", String.class)).isEqualTo(homeTenantId.toString());
        assertThat(claims.get("active_tenant_id", String.class)).isEqualTo(activeTenantId.toString());
        assertThat(claims.get("role", String.class)).isEqualTo(role);
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    void shouldIncludeDifferentRolesCorrectly() {
        // Given
        String email = "accountant@test.com";
        UUID homeTenantId = UUID.randomUUID();
        UUID activeTenantId = UUID.randomUUID();
        String role = "ACCOUNTANT";

        // When
        String token = tokenProvider.createToken(email, homeTenantId, activeTenantId, role);

        // Then
        Claims claims = Jwts.parser().verifyWith(tokenProvider.getSigningKey()).build()
                .parseSignedClaims(token).getPayload();

        assertThat(claims.get("role", String.class)).isEqualTo(role);
    }

    @Test
    void shouldCacheSigningKeyAfterInit() {
        // getSigningKey() returns a non-null cached key after init()
        SecretKey key = tokenProvider.getSigningKey();
        assertThat(key).isNotNull();

        // Same instance returned on repeated calls — no re-derivation
        assertThat(tokenProvider.getSigningKey()).isSameAs(key);
    }

    @Test
    void shouldRejectWeakSecretDuringInit() {
        // Given — weak secret, init() NOT yet called
        RiskGuardProperties weakProperties = new RiskGuardProperties();
        weakProperties.getSecurity().setJwtSecret("short");
        TokenProvider weakProvider = new TokenProvider(weakProperties);

        // When / Then — init() (the @PostConstruct method) rejects the secret at startup
        assertThatThrownBy(weakProvider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret too weak");
    }
}
