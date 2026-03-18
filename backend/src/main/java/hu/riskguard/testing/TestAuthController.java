package hu.riskguard.testing;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.security.TokenProvider;
import hu.riskguard.identity.domain.IdentityService;
import hu.riskguard.identity.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;

/**
 * Test-only authentication bypass controller.
 *
 * <p>Accepts {@code POST /api/test/auth/login} with an email and role,
 * looks up the user by email (must already exist via test seeding),
 * and issues the same HttpOnly {@code auth_token} JWT cookie that the
 * production OAuth2 success handler would issue — without requiring a
 * real Google/Microsoft SSO redirect.
 *
 * <p><strong>Security:</strong> This controller is ONLY active when
 * {@code SPRING_PROFILES_ACTIVE=test}. It returns 404 in all other profiles
 * because Spring will not instantiate the bean.
 *
 * @see TestSecurityConfig — permits {@code /api/test/**} without authentication
 */
@Slf4j
@RestController
@RequestMapping("/api/test/auth")
@Profile({"test", "e2e"})
@RequiredArgsConstructor
public class TestAuthController {

    private final TokenProvider tokenProvider;
    private final IdentityService identityService;
    private final RiskGuardProperties properties;

    public record TestLoginRequest(
            @NotBlank @Email String email,
            @NotBlank String role
    ) {}

    public record TestLoginResponse(
            String userId,
            String email,
            String role,
            String tenantId
    ) {}

    /**
     * Masks an email address for safe logging: shows only the last 3 chars of the local part.
     * E.g. "e2e@riskguard.hu" → "***e2e@..."
     */
    private static String maskEmail(String email) {
        if (email == null) return "null";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        String suffix = local.length() <= 3 ? local : local.substring(local.length() - 3);
        return suffix + "@...";
    }

    /**
     * Issues a JWT cookie for the given test user without OAuth2 redirect.
     * The user must already exist in the database (seeded by Flyway test migration).
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody TestLoginRequest request) {
        log.info("Test auth bypass login for email=***{}", maskEmail(request.email()));

        Optional<User> maybeUser = identityService.findUserByEmail(request.email());
        if (maybeUser.isEmpty()) {
            log.error("Test login failed: no user found with email=***{}", maskEmail(request.email()));
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND,
                    "Test user not found. Ensure test data seeding has run (R__e2e_test_data.sql)."
            );
            problem.setType(URI.create("urn:riskguard:error:test-user-not-found"));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
        }
        User user = maybeUser.get();

        // Use the requested role (allows testing different roles) or fall back to user's stored role
        String role = request.role() != null ? request.role() : user.getRole();

        String tier = identityService.findTenantTier(user.getTenantId());
        String token = tokenProvider.createToken(
                user.getEmail(),
                user.getId(),
                user.getTenantId(),
                user.getTenantId(),  // active_tenant_id = home_tenant_id (same as production SSO login)
                role,
                tier != null ? tier : properties.getIdentity().getDefaultTier()
        );

        ResponseCookie cookie = ResponseCookie.from(properties.getIdentity().getCookieName(), token)
                .path("/")
                .maxAge(properties.getSecurity().getJwtExpirationMs() / 1000)
                .secure(false)  // Always false in test — no TLS in local/CI
                .httpOnly(true)
                .sameSite("Lax")
                .build();

        TestLoginResponse body = new TestLoginResponse(
                user.getId().toString(),
                user.getEmail(),
                role,
                user.getTenantId().toString()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }
}
