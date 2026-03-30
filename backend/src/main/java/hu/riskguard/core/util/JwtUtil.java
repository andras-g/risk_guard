package hu.riskguard.core.util;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Utility for extracting and validating JWT claims in REST controllers.
 * Stateless static methods — no Spring context required.
 */
public final class JwtUtil {

    private JwtUtil() {
        // Static utility — no instantiation
    }

    /**
     * Extract a UUID claim from the JWT and validate it.
     * Throws {@code 401 Unauthorized} if the claim is absent or not a valid UUID.
     *
     * @param jwt       the authenticated user's JWT
     * @param claimName the JWT claim key (e.g. {@code "active_tenant_id"}, {@code "user_id"})
     * @return the claim value as a {@link UUID}
     * @throws ResponseStatusException 401 if the claim is missing or malformed
     */
    public static UUID requireUuidClaim(Jwt jwt, String claimName) {
        String claimValue = jwt.getClaimAsString(claimName);
        if (claimValue == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing " + claimName + " claim in JWT");
        }
        try {
            return UUID.fromString(claimValue);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid " + claimName + " claim in JWT: not a valid UUID");
        }
    }
}
