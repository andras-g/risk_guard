package hu.riskguard.identity.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.security.AuthCookieHelper;
import hu.riskguard.core.security.TokenProvider;
import hu.riskguard.identity.api.dto.LoginRequest;
import hu.riskguard.identity.api.dto.RegisterRequest;
import hu.riskguard.identity.api.dto.UserResponse;
import hu.riskguard.identity.domain.IdentityService;
import hu.riskguard.identity.domain.LoginAttemptService;
import hu.riskguard.identity.domain.User;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private IdentityService identityService;
    @Mock
    private TokenProvider tokenProvider;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private HttpServletResponse servletResponse;

    private PasswordEncoder passwordEncoder;
    private RiskGuardProperties properties;
    private AuthController controller;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "P@ssword1";
    private static final String TEST_NAME = "Test User";

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4); // Low strength for fast tests
        properties = new RiskGuardProperties();
        properties.getIdentity().setCookieName("auth_token");
        properties.getIdentity().setRefreshCookieName("refresh_token");
        properties.getSecurity().setJwtExpirationMs(3600000L);
        properties.getSecurity().setRefreshTokenExpirationDays(30);
        properties.getSecurity().setCookieSecure(false);
        AuthCookieHelper authCookieHelper = new AuthCookieHelper(properties);
        controller = new AuthController(identityService, tokenProvider, passwordEncoder, loginAttemptService, properties, authCookieHelper);
    }

    private User createTestUser(String email, String name, String ssoProvider, String passwordHash) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(UUID.randomUUID());
        user.setEmail(email);
        user.setName(name);
        user.setRole("SME_ADMIN");
        user.setPreferredLanguage("hu");
        user.setSsoProvider(ssoProvider);
        user.setPasswordHash(passwordHash);
        return user;
    }

    @Nested
    class Registration {

        @Test
        void registerSuccessShouldReturn201WithCookie() {
            // Given
            RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD, TEST_NAME);
            when(identityService.findSsoProviderByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

            User savedUser = createTestUser(TEST_EMAIL, TEST_NAME, "local", null);
            when(identityService.registerLocalUser(TEST_EMAIL, TEST_PASSWORD, TEST_NAME)).thenReturn(savedUser);
            when(identityService.findTenantTier(savedUser.getTenantId())).thenReturn("ALAP");
            when(tokenProvider.createToken(eq(TEST_EMAIL), eq(savedUser.getId()),
                    eq(savedUser.getTenantId()), eq(savedUser.getTenantId()), eq("SME_ADMIN"), eq("ALAP")))
                    .thenReturn("jwt-token");
            when(identityService.issueRefreshToken(savedUser.getId(), savedUser.getTenantId()))
                    .thenReturn("refresh-token-value");

            // When
            ResponseEntity<?> result = controller.register(request, servletResponse);

            // Then
            assertThat(result.getStatusCode().value()).isEqualTo(201);
            assertThat(result.getBody()).isInstanceOf(UserResponse.class);

            UserResponse body = (UserResponse) result.getBody();
            assertThat(body.email()).isEqualTo(TEST_EMAIL);
            assertThat(body.name()).isEqualTo(TEST_NAME);

            // Verify HttpOnly cookies set (access + refresh)
            ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
            verify(servletResponse, atLeast(2)).addHeader(eq("Set-Cookie"), headerCaptor.capture());
            boolean hasAccessCookie = headerCaptor.getAllValues().stream()
                    .anyMatch(c -> c.contains("auth_token=jwt-token") && c.contains("HttpOnly") && c.contains("SameSite=Lax"));
            assertThat(hasAccessCookie).as("Expected access token cookie").isTrue();
            boolean hasRefreshCookie = headerCaptor.getAllValues().stream()
                    .anyMatch(c -> c.contains("refresh_token=") && c.contains("HttpOnly"));
            assertThat(hasRefreshCookie).as("Expected refresh token cookie").isTrue();
        }

        @Test
        void registerDuplicateLocalEmailShouldReturn409() {
            // Given — email exists with local provider (AC #3)
            RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD, TEST_NAME);
            when(identityService.findSsoProviderByEmail(TEST_EMAIL)).thenReturn(Optional.of("local"));

            // When
            ResponseEntity<?> result = controller.register(request, servletResponse);

            // Then
            assertThat(result.getStatusCode().value()).isEqualTo(409);
            ProblemDetail problem = (ProblemDetail) result.getBody();
            assertThat(problem.getType().toString()).isEqualTo("urn:riskguard:error:email-already-registered");

            // No user creation should have been attempted
            verify(identityService, never()).registerLocalUser(any(), any(), any());
        }

        @Test
        void registerSsoEmailShouldReturn409WithSsoHint() {
            // Given — email exists with Google SSO (AC #7)
            RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD, TEST_NAME);
            when(identityService.findSsoProviderByEmail(TEST_EMAIL)).thenReturn(Optional.of("google"));

            // When
            ResponseEntity<?> result = controller.register(request, servletResponse);

            // Then
            assertThat(result.getStatusCode().value()).isEqualTo(409);
            ProblemDetail problem = (ProblemDetail) result.getBody();
            assertThat(problem.getType().toString()).isEqualTo("urn:riskguard:error:email-exists-sso");
            // Provider name is NOT included in the detail to prevent user enumeration (security fix)
            assertThat(problem.getDetail()).doesNotContain("google");
            assertThat(problem.getDetail()).contains("SSO provider");

            verify(identityService, never()).registerLocalUser(any(), any(), any());
        }

        @Test
        void registerRequestValidation_passwordsMustMatch() {
            // Given — confirmPassword does not match password (server-side guard)
            RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, "DifferentP@ss1", TEST_NAME);

            // Then — @AssertTrue on isPasswordsMatch() should be false
            assertThat(request.isPasswordsMatch()).isFalse();
        }

        @Test
        void registerRequestValidation_passwordsMatch() {
            // Given — confirmPassword matches password
            RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD, TEST_NAME);

            // Then — @AssertTrue on isPasswordsMatch() should be true
            assertThat(request.isPasswordsMatch()).isTrue();
        }

        @Test
        void registerNormalizesEmailToLowercase() {
            // Given — mixed-case email in request should be normalized before DB lookups
            String mixedCaseEmail = "Test@Example.COM";
            String normalizedEmail = "test@example.com";
            RegisterRequest request = new RegisterRequest(mixedCaseEmail, TEST_PASSWORD, TEST_PASSWORD, TEST_NAME);

            when(identityService.findSsoProviderByEmail(normalizedEmail)).thenReturn(Optional.empty());
            User savedUser = createTestUser(normalizedEmail, TEST_NAME, "local", null);
            when(identityService.registerLocalUser(normalizedEmail, TEST_PASSWORD, TEST_NAME)).thenReturn(savedUser);
            when(identityService.findTenantTier(any())).thenReturn("ALAP");
            when(tokenProvider.createToken(any(), any(), any(), any(), any(), any())).thenReturn("jwt-token");
            when(identityService.issueRefreshToken(any(), any())).thenReturn("refresh-token");

            // When
            ResponseEntity<?> result = controller.register(request, servletResponse);

            // Then — registration succeeds with normalized email
            assertThat(result.getStatusCode().value()).isEqualTo(201);
            // Verify normalized email was used for both DB check and user creation
            verify(identityService).findSsoProviderByEmail(normalizedEmail);
            verify(identityService).registerLocalUser(normalizedEmail, TEST_PASSWORD, TEST_NAME);
        }
    }

    @Nested
    class Login {

        @Test
        void loginSuccessShouldReturn200WithCookie() {
            // Given
            String hashedPassword = passwordEncoder.encode(TEST_PASSWORD);
            User user = createTestUser(TEST_EMAIL, TEST_NAME, "local", hashedPassword);
            LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

            when(loginAttemptService.isLockedOut(TEST_EMAIL)).thenReturn(false);
            when(identityService.findUserByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(identityService.findTenantTier(user.getTenantId())).thenReturn("ALAP");
            when(tokenProvider.createToken(eq(TEST_EMAIL), eq(user.getId()),
                    eq(user.getTenantId()), eq(user.getTenantId()), eq("SME_ADMIN"), eq("ALAP")))
                    .thenReturn("jwt-token");
            when(identityService.issueRefreshToken(user.getId(), user.getTenantId()))
                    .thenReturn("refresh-token-value");

            // When
            ResponseEntity<?> result = controller.login(request, servletResponse);

            // Then
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).isInstanceOf(UserResponse.class);

            verify(loginAttemptService).resetAttempts(TEST_EMAIL);

            // Verify both cookies set (access + refresh)
            ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
            verify(servletResponse, atLeast(2)).addHeader(eq("Set-Cookie"), headerCaptor.capture());
            boolean hasAccessCookie = headerCaptor.getAllValues().stream()
                    .anyMatch(c -> c.contains("auth_token=jwt-token"));
            assertThat(hasAccessCookie).as("Expected access token cookie").isTrue();
        }

        @Test
        void loginWrongPasswordShouldReturn401Generic() {
            // Given (AC #5)
            String hashedPassword = passwordEncoder.encode(TEST_PASSWORD);
            User user = createTestUser(TEST_EMAIL, TEST_NAME, "local", hashedPassword);
            LoginRequest request = new LoginRequest(TEST_EMAIL, "WrongP@ss1");

            when(loginAttemptService.isLockedOut(TEST_EMAIL)).thenReturn(false);
            when(identityService.findUserByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

            // When
            ResponseEntity<?> result = controller.login(request, servletResponse);

            // Then
            assertThat(result.getStatusCode().value()).isEqualTo(401);
            ProblemDetail problem = (ProblemDetail) result.getBody();
            assertThat(problem.getType().toString()).isEqualTo("urn:riskguard:error:invalid-credentials");

            verify(loginAttemptService).recordFailedAttempt(TEST_EMAIL);
            verify(tokenProvider, never()).createToken(any(), any(), any(), any(), any(), any());
        }

        @Test
        void loginNonExistentUserShouldReturn401GenericTimingSafe() {
            // Given — user doesn't exist (AC #5, timing-safe)
            LoginRequest request = new LoginRequest("nobody@example.com", TEST_PASSWORD);

            when(loginAttemptService.isLockedOut("nobody@example.com")).thenReturn(false);
            when(identityService.findUserByEmail("nobody@example.com")).thenReturn(Optional.empty());

            // When
            ResponseEntity<?> result = controller.login(request, servletResponse);

            // Then — same 401 as wrong password
            assertThat(result.getStatusCode().value()).isEqualTo(401);
            ProblemDetail problem = (ProblemDetail) result.getBody();
            assertThat(problem.getType().toString()).isEqualTo("urn:riskguard:error:invalid-credentials");

            verify(loginAttemptService).recordFailedAttempt("nobody@example.com");
        }

        @Test
        void loginSsoUserShouldReturn401Generic() {
            // Given — user exists but with SSO provider, not local
            User ssoUser = createTestUser(TEST_EMAIL, TEST_NAME, "google", null);
            LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

            when(loginAttemptService.isLockedOut(TEST_EMAIL)).thenReturn(false);
            when(identityService.findUserByEmail(TEST_EMAIL)).thenReturn(Optional.of(ssoUser));

            // When
            ResponseEntity<?> result = controller.login(request, servletResponse);

            // Then — same generic 401, does not reveal that the account exists via SSO
            assertThat(result.getStatusCode().value()).isEqualTo(401);
            ProblemDetail problem = (ProblemDetail) result.getBody();
            assertThat(problem.getType().toString()).isEqualTo("urn:riskguard:error:invalid-credentials");

            verify(loginAttemptService).recordFailedAttempt(TEST_EMAIL);
        }

        @Test
        void loginBruteForceLockedOutShouldReturn429() {
            // Given — 5 failed attempts already (AC #10)
            LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);
            when(loginAttemptService.isLockedOut(TEST_EMAIL)).thenReturn(true);

            // When
            ResponseEntity<?> result = controller.login(request, servletResponse);

            // Then
            assertThat(result.getStatusCode().value()).isEqualTo(429);
            ProblemDetail problem = (ProblemDetail) result.getBody();
            assertThat(problem.getType().toString()).isEqualTo("urn:riskguard:error:too-many-attempts");

            // Should NOT even attempt to look up user
            verify(identityService, never()).findUserByEmail(any());
        }

        @Test
        void loginSuccessShouldResetFailedAttempts() {
            // Given
            String hashedPassword = passwordEncoder.encode(TEST_PASSWORD);
            User user = createTestUser(TEST_EMAIL, TEST_NAME, "local", hashedPassword);
            LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

            when(loginAttemptService.isLockedOut(TEST_EMAIL)).thenReturn(false);
            when(identityService.findUserByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(identityService.findTenantTier(any())).thenReturn("ALAP");
            when(tokenProvider.createToken(any(), any(), any(), any(), any(), any())).thenReturn("jwt-token");
            when(identityService.issueRefreshToken(any(), any())).thenReturn("refresh-token");

            // When
            controller.login(request, servletResponse);

            // Then — failed attempts counter must be reset
            verify(loginAttemptService).resetAttempts(TEST_EMAIL);
        }

        @Test
        void loginNormalizesEmailToLowercase() {
            // Given — email with mixed case should be normalized before all operations
            String hashedPassword = passwordEncoder.encode(TEST_PASSWORD);
            String mixedCaseEmail = "Test@Example.COM";
            String normalizedEmail = "test@example.com";
            User user = createTestUser(normalizedEmail, TEST_NAME, "local", hashedPassword);
            LoginRequest request = new LoginRequest(mixedCaseEmail, TEST_PASSWORD);

            when(loginAttemptService.isLockedOut(normalizedEmail)).thenReturn(false);
            when(identityService.findUserByEmail(normalizedEmail)).thenReturn(Optional.of(user));
            when(identityService.findTenantTier(any())).thenReturn("ALAP");
            when(tokenProvider.createToken(any(), any(), any(), any(), any(), any())).thenReturn("jwt-token");
            when(identityService.issueRefreshToken(any(), any())).thenReturn("refresh-token");

            // When
            ResponseEntity<?> result = controller.login(request, servletResponse);

            // Then — all operations use normalized (lowercase) email
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            verify(loginAttemptService).isLockedOut(normalizedEmail);
            verify(identityService).findUserByEmail(normalizedEmail);
            verify(loginAttemptService).resetAttempts(normalizedEmail);
        }
    }

    @Nested
    class LoginAttemptServiceBehavior {

        @Test
        void lockoutCanBeResetOnSuccessfulLogin() {
            // Given — using a real LoginAttemptService
            // Verifies that a successful login clears the lockout counter.
            LoginAttemptService realService = new LoginAttemptService();

            // Record 5 failed attempts → account locked
            for (int i = 0; i < 5; i++) {
                realService.recordFailedAttempt(TEST_EMAIL);
            }
            assertThat(realService.isLockedOut(TEST_EMAIL)).isTrue();

            // When — successful login resets the counter
            realService.resetAttempts(TEST_EMAIL);

            // Then — lockout is cleared
            assertThat(realService.isLockedOut(TEST_EMAIL)).isFalse();
        }

        @Test
        void lockoutExpiresAfterTTL() {
            // Given — inject a Caffeine cache with a controllable fake ticker so we can
            // advance time without actually sleeping 15 minutes (AC #10).
            AtomicLong fakeNanos = new AtomicLong(0);
            Cache<String, AtomicInteger> testCache = Caffeine.newBuilder()
                    .expireAfterWrite(15, TimeUnit.MINUTES)
                    .ticker(fakeNanos::get) // fake ticker — 0 nanos initially
                    .maximumSize(10_000)
                    .build();
            LoginAttemptService realService = new LoginAttemptService(testCache);

            // Record 5 failed attempts → account locked
            for (int i = 0; i < 5; i++) {
                realService.recordFailedAttempt(TEST_EMAIL);
            }
            assertThat(realService.isLockedOut(TEST_EMAIL)).isTrue();

            // Advance fake time past the 15-minute TTL
            fakeNanos.set(TimeUnit.MINUTES.toNanos(16));
            // Caffeine evicts lazily — trigger cleanup by calling the cache
            testCache.cleanUp();

            // Then — lockout should have expired (cache entry evicted)
            assertThat(realService.isLockedOut(TEST_EMAIL)).isFalse();
        }

        @Test
        void lockoutNotTriggeredBelow5Attempts() {
            // Given
            LoginAttemptService realService = new LoginAttemptService();

            // Record 4 failed attempts (below threshold)
            for (int i = 0; i < 4; i++) {
                realService.recordFailedAttempt(TEST_EMAIL);
            }

            // Then — not yet locked out
            assertThat(realService.isLockedOut(TEST_EMAIL)).isFalse();
        }

        @Test
        void lockoutTriggeredAtExactly5Attempts() {
            // Given
            LoginAttemptService realService = new LoginAttemptService();

            // Record exactly 5 failed attempts
            for (int i = 0; i < 5; i++) {
                realService.recordFailedAttempt(TEST_EMAIL);
            }

            // Then — locked out
            assertThat(realService.isLockedOut(TEST_EMAIL)).isTrue();
        }

        @Test
        void lockoutIsCaseInsensitive() {
            // Given — attacker uses different email case variations
            LoginAttemptService realService = new LoginAttemptService();

            realService.recordFailedAttempt("user@example.com");
            realService.recordFailedAttempt("User@Example.com");
            realService.recordFailedAttempt("USER@EXAMPLE.COM");
            realService.recordFailedAttempt("user@EXAMPLE.com");
            realService.recordFailedAttempt("User@example.COM");

            // Then — all counted as same email, now locked out
            assertThat(realService.isLockedOut("user@example.com")).isTrue();
            assertThat(realService.isLockedOut("USER@EXAMPLE.COM")).isTrue();
        }
    }
}
