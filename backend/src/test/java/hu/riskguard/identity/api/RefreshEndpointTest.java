package hu.riskguard.identity.api;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.security.AuthCookieHelper;
import hu.riskguard.core.security.TokenProvider;
import hu.riskguard.identity.domain.IdentityService;
import hu.riskguard.identity.domain.LoginAttemptService;
import hu.riskguard.identity.domain.RefreshTokenService;
import hu.riskguard.identity.domain.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the refresh endpoint in {@link AuthController}.
 * Story 3.13 — Task 10.2
 */
@ExtendWith(MockitoExtension.class)
class RefreshEndpointTest {

    @Mock
    private IdentityService identityService;
    @Mock
    private TokenProvider tokenProvider;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private HttpServletResponse servletResponse;

    private RiskGuardProperties properties;
    private AuthController controller;
    private MockHttpServletRequest servletRequest;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FAMILY_ID = UUID.randomUUID();
    private static final String USER_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        properties = new RiskGuardProperties();
        properties.getIdentity().setCookieName("auth_token");
        properties.getIdentity().setRefreshCookieName("refresh_token");
        properties.getSecurity().setJwtExpirationMs(900000L); // 15 min
        properties.getSecurity().setRefreshTokenExpirationDays(30);
        properties.getSecurity().setCookieSecure(false);

        AuthCookieHelper authCookieHelper = new AuthCookieHelper(properties);
        controller = new AuthController(
                identityService, tokenProvider,
                new BCryptPasswordEncoder(4), loginAttemptService, properties, authCookieHelper);

        servletRequest = new MockHttpServletRequest();
    }

    // --- Successful Rotation ---

    @Test
    void refresh_validToken_shouldReturn204AndSetBothCookies() {
        servletRequest.setCookies(new Cookie("refresh_token", "valid-raw-token"));

        when(identityService.rotateRefreshToken("valid-raw-token"))
                .thenReturn(new RefreshTokenService.RotationResult.Success(
                        "new-raw-token", USER_ID, TENANT_ID, FAMILY_ID));
        when(identityService.getUserEmail(USER_ID)).thenReturn(USER_EMAIL);

        User user = createTestUser();
        when(identityService.findUserByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
        when(identityService.findTenantTier(TENANT_ID)).thenReturn("ALAP");
        when(tokenProvider.createToken(eq(USER_EMAIL), eq(USER_ID), eq(TENANT_ID),
                eq(TENANT_ID), eq("SME_ADMIN"), eq("ALAP")))
                .thenReturn("new-access-jwt");

        ResponseEntity<?> result = controller.refresh(servletRequest, servletResponse);

        assertThat(result.getStatusCode().value()).isEqualTo(204);

        // Verify both cookies are set
        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(servletResponse, org.mockito.Mockito.atLeast(2))
                .addHeader(eq("Set-Cookie"), headerCaptor.capture());

        boolean hasAccessCookie = headerCaptor.getAllValues().stream()
                .anyMatch(c -> c.contains("auth_token=new-access-jwt"));
        boolean hasRefreshCookie = headerCaptor.getAllValues().stream()
                .anyMatch(c -> c.contains("refresh_token=new-raw-token"));
        assertThat(hasAccessCookie).isTrue();
        assertThat(hasRefreshCookie).isTrue();
    }

    // --- Missing Cookie ---

    @Test
    void refresh_missingCookie_shouldReturn401() {
        // No cookies set on request
        ResponseEntity<?> result = controller.refresh(servletRequest, servletResponse);

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        assertThat(result.getBody()).isInstanceOf(ProblemDetail.class);
        ProblemDetail problem = (ProblemDetail) result.getBody();
        assertThat(problem.getTitle()).isEqualTo("Missing refresh token");
    }

    // --- Token Family Revoked (Reuse Detection) ---

    @Test
    void refresh_revokedToken_shouldReturn401WithFamilyRevokedCode() {
        servletRequest.setCookies(new Cookie("refresh_token", "stolen-token"));

        when(identityService.rotateRefreshToken("stolen-token"))
                .thenReturn(new RefreshTokenService.RotationResult.FamilyRevoked(USER_ID));

        ResponseEntity<?> result = controller.refresh(servletRequest, servletResponse);

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        assertThat(result.getBody()).isInstanceOf(ProblemDetail.class);
        ProblemDetail problem = (ProblemDetail) result.getBody();
        assertThat(problem.getProperties()).containsEntry("errorCode", "TOKEN_FAMILY_REVOKED");
    }

    // --- Expired Token ---

    @Test
    void refresh_expiredToken_shouldReturn401WithExpiredCode() {
        servletRequest.setCookies(new Cookie("refresh_token", "expired-token"));

        when(identityService.rotateRefreshToken("expired-token"))
                .thenReturn(new RefreshTokenService.RotationResult.Expired());

        ResponseEntity<?> result = controller.refresh(servletRequest, servletResponse);

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        assertThat(result.getBody()).isInstanceOf(ProblemDetail.class);
        ProblemDetail problem = (ProblemDetail) result.getBody();
        assertThat(problem.getProperties()).containsEntry("errorCode", "REFRESH_TOKEN_EXPIRED");
    }

    // --- Invalid Token ---

    @Test
    void refresh_invalidToken_shouldReturn401() {
        servletRequest.setCookies(new Cookie("refresh_token", "garbage-token"));

        when(identityService.rotateRefreshToken("garbage-token"))
                .thenReturn(new RefreshTokenService.RotationResult.Invalid());

        ResponseEntity<?> result = controller.refresh(servletRequest, servletResponse);

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        assertThat(result.getBody()).isInstanceOf(ProblemDetail.class);
        ProblemDetail problem = (ProblemDetail) result.getBody();
        assertThat(problem.getTitle()).isEqualTo("Invalid refresh token");
    }

    // --- Helpers ---

    private User createTestUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        user.setEmail(USER_EMAIL);
        user.setRole("SME_ADMIN");
        user.setSsoProvider("local");
        return user;
    }
}
