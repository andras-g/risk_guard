package hu.riskguard.testing;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.security.TokenProvider;
import hu.riskguard.identity.domain.IdentityService;
import hu.riskguard.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestAuthControllerTest {

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private IdentityService identityService;

    private RiskGuardProperties properties;

    private TestAuthController controller;

    @BeforeEach
    void setUp() {
        properties = new RiskGuardProperties();
        properties.getIdentity().setCookieName("auth_token");
        properties.getSecurity().setJwtExpirationMs(3600000L);
        controller = new TestAuthController(tokenProvider, identityService, properties);
    }

    @Test
    void login_withExistingUser_issuesJwtCookieAndReturnsUserInfo() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("test@example.com");
        user.setRole("SME_ADMIN");
        user.setTenantId(tenantId);

        when(identityService.findUserByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(identityService.findTenantTier(tenantId)).thenReturn("ALAP");
        when(tokenProvider.createToken(eq("test@example.com"), eq(userId), eq(tenantId), eq(tenantId), eq("SME_ADMIN"), eq("ALAP")))
                .thenReturn("test-jwt-token");

        var request = new TestAuthController.TestLoginRequest("test@example.com", "SME_ADMIN");

        // Act
        ResponseEntity<?> response = controller.login(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        // Verify response body
        assertThat(response.getBody()).isInstanceOf(TestAuthController.TestLoginResponse.class);
        TestAuthController.TestLoginResponse body = (TestAuthController.TestLoginResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.email()).isEqualTo("test@example.com");
        assertThat(body.role()).isEqualTo("SME_ADMIN");
        assertThat(body.userId()).isEqualTo(userId.toString());
        assertThat(body.tenantId()).isEqualTo(tenantId.toString());

        // Verify Set-Cookie header contains auth_token
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("auth_token=test-jwt-token");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(setCookie).contains("Path=/");
    }

    @Test
    void login_withNonExistentUser_returns404WithProblemDetail() {
        when(identityService.findUserByEmail("missing@example.com")).thenReturn(Optional.empty());

        var request = new TestAuthController.TestLoginRequest("missing@example.com", "SME_ADMIN");

        ResponseEntity<?> response = controller.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getDetail()).contains("Ensure test data seeding has run");
        assertThat(problem.getType().toString()).isEqualTo("urn:riskguard:error:test-user-not-found");
    }

    @Test
    void login_withCustomRole_usesRequestedRole() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("accountant@example.com");
        user.setRole("SME_ADMIN");
        user.setTenantId(tenantId);

        when(identityService.findUserByEmail("accountant@example.com")).thenReturn(Optional.of(user));
        when(identityService.findTenantTier(tenantId)).thenReturn("ALAP");
        when(tokenProvider.createToken(eq("accountant@example.com"), eq(userId), eq(tenantId), eq(tenantId), eq("ACCOUNTANT"), eq("ALAP")))
                .thenReturn("accountant-jwt-token");

        var request = new TestAuthController.TestLoginRequest("accountant@example.com", "ACCOUNTANT");

        // Act
        ResponseEntity<?> response = controller.login(request);

        // Assert
        assertThat(response.getBody()).isNotNull().isInstanceOf(TestAuthController.TestLoginResponse.class);
        TestAuthController.TestLoginResponse body = (TestAuthController.TestLoginResponse) response.getBody();
        assertThat(body.role()).isEqualTo("ACCOUNTANT");
    }
}
