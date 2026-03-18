package hu.riskguard.identity.api;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.security.TokenProvider;
import hu.riskguard.identity.api.dto.TenantResponse;
import hu.riskguard.identity.api.dto.TenantSwitchRequest;
import hu.riskguard.identity.domain.events.TenantContextSwitchedEvent;
import hu.riskguard.identity.domain.IdentityService;
import hu.riskguard.identity.domain.User;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityControllerTest {

    @Mock
    private IdentityService identityService;
    @Mock
    private TokenProvider tokenProvider;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private HttpServletResponse servletResponse;

    private RiskGuardProperties properties;
    private IdentityController controller;

    @BeforeEach
    void setUp() {
        properties = new RiskGuardProperties();
        properties.getIdentity().setCookieName("auth_token");
        properties.getSecurity().setJwtExpirationMs(3600000L);
        properties.getSecurity().setCookieSecure(false);
        controller = new IdentityController(identityService, tokenProvider, properties, eventPublisher);
    }

    @Test
    void logoutShouldClearAuthCookieWithMaxAgeZero() {
        // When
        ResponseEntity<Void> result = controller.logout(servletResponse);

        // Then — 204 returned and deletion cookie set
        assertThat(result.getStatusCode().value()).isEqualTo(204);

        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(servletResponse).addHeader(eq("Set-Cookie"), headerCaptor.capture());

        String setCookie = headerCaptor.getValue();
        assertThat(setCookie).contains("auth_token=");
        assertThat(setCookie).contains("Max-Age=0");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");
    }

    @Test
    void switchTenantShouldPublishTenantContextSwitchedEvent() {
        // Given — uses ACCOUNTANT role so external tenant switch is permitted
        UUID userId = UUID.randomUUID();
        UUID homeTenantId = UUID.randomUUID();
        UUID previousActiveTenantId = UUID.randomUUID();
        UUID newTenantId = UUID.randomUUID();
        String email = "accountant@test.com";
        String role = "ACCOUNTANT";

        User user = new User();
        user.setId(userId);
        user.setTenantId(homeTenantId);
        user.setEmail(email);
        user.setRole(role);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(email)
                .claim("active_tenant_id", previousActiveTenantId.toString())
                .claim("home_tenant_id", homeTenantId.toString())
                .claim("role", role)
                .build();

        when(identityService.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(identityService.hasMandate(userId, newTenantId)).thenReturn(true);
        when(identityService.findTenantTier(newTenantId)).thenReturn("ALAP");
        when(tokenProvider.createToken(eq(email), eq(userId), eq(homeTenantId), eq(newTenantId), eq(role), eq("ALAP")))
                .thenReturn("new-jwt-token");

        TenantSwitchRequest request = new TenantSwitchRequest(newTenantId);

        // When
        ResponseEntity<Void> result = controller.switchTenant(jwt, request, servletResponse);

        // Then — returns 204 No Content
        assertThat(result.getStatusCode().value()).isEqualTo(204);

        ArgumentCaptor<TenantContextSwitchedEvent> eventCaptor = ArgumentCaptor.forClass(TenantContextSwitchedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TenantContextSwitchedEvent event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(userId);
        // email is intentionally NOT in the event per PII zero-tolerance policy
        assertThat(event.previousTenantId()).isEqualTo(previousActiveTenantId);
        assertThat(event.newTenantId()).isEqualTo(newTenantId);
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void switchTenantToOwnTenantShouldAlsoPublishEvent() {
        // Given: user switches to their own (home) tenant — no mandate check needed
        UUID userId = UUID.randomUUID();
        UUID homeTenantId = UUID.randomUUID();
        UUID previousActiveTenantId = UUID.randomUUID();
        String email = "user@test.com";
        String role = "SME_ADMIN";

        User user = new User();
        user.setId(userId);
        user.setTenantId(homeTenantId);
        user.setEmail(email);
        user.setRole(role);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(email)
                .claim("active_tenant_id", previousActiveTenantId.toString())
                .claim("home_tenant_id", homeTenantId.toString())
                .claim("role", role)
                .build();

        when(identityService.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(identityService.findTenantTier(homeTenantId)).thenReturn("ALAP");
        when(tokenProvider.createToken(eq(email), eq(userId), eq(homeTenantId), eq(homeTenantId), eq(role), eq("ALAP")))
                .thenReturn("new-jwt-token");

        TenantSwitchRequest request = new TenantSwitchRequest(homeTenantId);

        // When
        ResponseEntity<Void> result = controller.switchTenant(jwt, request, servletResponse);

        // Then: event should still be published even for own tenant, 204 returned
        assertThat(result.getStatusCode().value()).isEqualTo(204);
        verify(eventPublisher).publishEvent(any(TenantContextSwitchedEvent.class));
    }

    @Test
    void switchTenantShouldPublishEventBeforeTokenCreation() {
        // Given — event publisher throws to simulate failure; uses ACCOUNTANT role to switch to external tenant
        UUID userId = UUID.randomUUID();
        UUID homeTenantId = UUID.randomUUID();
        UUID previousActiveTenantId = UUID.randomUUID();
        UUID newTenantId = UUID.randomUUID();
        String email = "accountant@test.com";
        String role = "ACCOUNTANT";

        User user = new User();
        user.setId(userId);
        user.setTenantId(homeTenantId);
        user.setEmail(email);
        user.setRole(role);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(email)
                .claim("active_tenant_id", previousActiveTenantId.toString())
                .claim("home_tenant_id", homeTenantId.toString())
                .claim("role", role)
                .build();

        when(identityService.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(identityService.hasMandate(userId, newTenantId)).thenReturn(true);
        doThrow(new RuntimeException("Event listener failed"))
                .when(eventPublisher).publishEvent(any(TenantContextSwitchedEvent.class));

        TenantSwitchRequest request = new TenantSwitchRequest(newTenantId);

        // When / Then — exception propagates, token is never created (audit event fires first)
        assertThatThrownBy(() -> controller.switchTenant(jwt, request, servletResponse))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Event listener failed");

        // Token should NOT have been created since event failed first
        verify(tokenProvider, never()).createToken(any(), any(), any(), any(), any(), any());
        // No cookie should be set on the response
        verify(servletResponse, never()).addHeader(any(), any());
    }

    @Test
    void switchTenantShouldForbidSmeAdminSwitchingToExternalTenant() {
        // Given: SME_ADMIN user attempting to switch to a different (external) tenant
        UUID userId = UUID.randomUUID();
        UUID homeTenantId = UUID.randomUUID();
        UUID externalTenantId = UUID.randomUUID();
        String email = "sme@test.com";
        String role = "SME_ADMIN";

        User user = new User();
        user.setId(userId);
        user.setTenantId(homeTenantId);
        user.setEmail(email);
        user.setRole(role);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(email)
                .claim("active_tenant_id", homeTenantId.toString())
                .claim("home_tenant_id", homeTenantId.toString())
                .claim("role", role)
                .build();

        when(identityService.findUserByEmail(email)).thenReturn(Optional.of(user));

        TenantSwitchRequest request = new TenantSwitchRequest(externalTenantId);

        // When / Then — FORBIDDEN: only ACCOUNTANT can switch to external tenants
        assertThatThrownBy(() -> controller.switchTenant(jwt, request, servletResponse))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("FORBIDDEN");

        // No event published, no token created
        verify(eventPublisher, never()).publishEvent(any());
        verify(tokenProvider, never()).createToken(any(), any(), any(), any(), any(), any());
    }

    @Test
    void getMandatesShouldForbidNonAccountant() {
        // Given — SME_ADMIN JWT: role claim is not ACCOUNTANT
        String email = "sme@test.com";

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(email)
                .claim("role", "SME_ADMIN")
                .build();

        // When / Then — FORBIDDEN thrown before any service call
        assertThatThrownBy(() -> controller.getMandates(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("Only ACCOUNTANT role can access mandates");

        verify(identityService, never()).findUserByEmail(any());
        verify(identityService, never()).findMandatedTenants(any());
    }

    @Test
    void getMandatesShouldReturnMandatesForAccountant() {
        // Given — ACCOUNTANT JWT
        UUID userId = UUID.randomUUID();
        UUID homeTenantId = UUID.randomUUID();
        String email = "accountant@test.com";

        User user = new User();
        user.setId(userId);
        user.setTenantId(homeTenantId);
        user.setEmail(email);
        user.setRole("ACCOUNTANT");

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(email)
                .claim("role", "ACCOUNTANT")
                .claim("active_tenant_id", homeTenantId.toString())
                .build();

        TenantResponse mandate1 = new TenantResponse(UUID.randomUUID(), "Client Alpha", "STANDARD");
        TenantResponse mandate2 = new TenantResponse(UUID.randomUUID(), "Client Beta", "PREMIUM");

        when(identityService.findUserByEmail(email)).thenReturn(Optional.of(user));
        when(identityService.findMandatedTenants(userId)).thenReturn(List.of(mandate1, mandate2));

        // When
        List<TenantResponse> result = controller.getMandates(jwt);

        // Then — both mandated tenants returned
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(mandate1, mandate2);
        verify(identityService).findUserByEmail(email);
        verify(identityService).findMandatedTenants(userId);
    }
}
