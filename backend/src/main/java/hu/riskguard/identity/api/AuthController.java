package hu.riskguard.identity.api;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.security.TokenProvider;
import hu.riskguard.identity.api.dto.LoginRequest;
import hu.riskguard.identity.api.dto.RegisterRequest;
import hu.riskguard.identity.api.dto.UserResponse;
import hu.riskguard.identity.domain.IdentityService;
import hu.riskguard.identity.domain.LoginAttemptService;
import hu.riskguard.identity.domain.User;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

/**
 * Public authentication controller for local (email/password) registration and login.
 * Mapped to /api/public/auth/** which is permitAll() in SecurityConfig.
 *
 * <p>Separated from IdentityController because these endpoints are PUBLIC
 * (no authentication required), while all IdentityController endpoints require auth.
 */
@RestController
@RequestMapping("/api/public/auth")
public class AuthController {

    private final IdentityService identityService;
    private final TokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final RiskGuardProperties properties;

    /**
     * Dummy BCrypt hash used for timing-safe comparison when user is not found.
     * Generated at startup using the injected PasswordEncoder to guarantee a valid BCrypt
     * structure with the correct cost factor. This ensures {@code matches()} always performs
     * a full BCrypt computation, preventing timing-based email enumeration.
     */
    private final String dummyHash;

    public AuthController(IdentityService identityService, TokenProvider tokenProvider,
                          PasswordEncoder passwordEncoder, LoginAttemptService loginAttemptService,
                          RiskGuardProperties properties) {
        this.identityService = identityService;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.properties = properties;
        // Generate a valid BCrypt hash at construction time for timing-safe comparisons
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        // Normalize email to lowercase for ALL operations — prevents duplicate registrations
        // via email case variations (e.g., "User@Example.com" vs "user@example.com")
        String normalizedEmail = request.email().toLowerCase();

        // Check if email already exists
        Optional<String> existingProvider = identityService.findSsoProviderByEmail(normalizedEmail);

        if (existingProvider.isPresent()) {
            String provider = existingProvider.get();
            if ("local".equals(provider)) {
                // AC #3: Email already registered with local auth
                ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
                problem.setType(URI.create("urn:riskguard:error:email-already-registered"));
                problem.setTitle("Email already registered");
                problem.setDetail("This email address is already registered.");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
            } else {
                // AC #7: Email exists with SSO provider — do not reveal which provider (user enumeration risk)
                ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
                problem.setType(URI.create("urn:riskguard:error:email-exists-sso"));
                problem.setTitle("Email registered via SSO");
                problem.setDetail("This email is registered via an SSO provider. Please use your SSO provider to sign in.");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
            }
        }

        // Create user, tenant, and mandate
        User user = identityService.registerLocalUser(normalizedEmail, request.password(), request.name());

        // Issue JWT HttpOnly cookie (same flow as SSO)
        String tier = identityService.findTenantTier(user.getTenantId());
        String token = tokenProvider.createToken(
                user.getEmail(), user.getId(), user.getTenantId(), user.getTenantId(), user.getRole(),
                tier != null ? tier : properties.getIdentity().getDefaultTier());
        setAuthCookie(response, token);

        UserResponse userResponse = UserResponse.from(user, user.getTenantId().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(userResponse);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        // Normalize email to lowercase for ALL operations — prevents brute-force lockout bypass
        // via case variations (e.g., "User@Example.com" vs "user@example.com")
        String normalizedEmail = request.email().toLowerCase();

        // AC #10: Check brute-force lockout BEFORE any other processing
        if (loginAttemptService.isLockedOut(normalizedEmail)) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
            problem.setType(URI.create("urn:riskguard:error:too-many-attempts"));
            problem.setTitle("Too many login attempts");
            problem.setDetail("Too many failed login attempts. Please try again later.");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problem);
        }

        Optional<User> userOpt = identityService.findUserByEmail(normalizedEmail);

        if (userOpt.isEmpty()) {
            // AC #5: Timing-safe — compare against dummy hash to prevent timing attacks
            passwordEncoder.matches(request.password(), dummyHash);
            loginAttemptService.recordFailedAttempt(normalizedEmail);
            return invalidCredentialsResponse();
        }

        User user = userOpt.get();

        // AC: If user exists but is SSO-only, return same generic error
        if (!"local".equals(user.getSsoProvider())) {
            passwordEncoder.matches(request.password(), dummyHash);
            loginAttemptService.recordFailedAttempt(normalizedEmail);
            return invalidCredentialsResponse();
        }

        // Verify password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailedAttempt(normalizedEmail);
            return invalidCredentialsResponse();
        }

        // Success — reset failed attempts and issue token
        loginAttemptService.resetAttempts(normalizedEmail);

        String tier = identityService.findTenantTier(user.getTenantId());
        String token = tokenProvider.createToken(
                user.getEmail(), user.getId(), user.getTenantId(), user.getTenantId(), user.getRole(),
                tier != null ? tier : properties.getIdentity().getDefaultTier());
        setAuthCookie(response, token);

        UserResponse userResponse = UserResponse.from(user, user.getTenantId().toString());
        return ResponseEntity.ok(userResponse);
    }

    private ResponseEntity<ProblemDetail> invalidCredentialsResponse() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setType(URI.create("urn:riskguard:error:invalid-credentials"));
        problem.setTitle("Invalid credentials");
        problem.setDetail("Invalid email or password.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    private void setAuthCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(properties.getIdentity().getCookieName(), token)
                .path("/")
                .maxAge(properties.getSecurity().getJwtExpirationMs() / 1000)
                .secure(properties.getSecurity().isCookieSecure())
                .httpOnly(true)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
