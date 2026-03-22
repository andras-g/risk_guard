package hu.riskguard.core.security;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.identity.domain.IdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private TokenProvider tokenProvider;
    @Mock
    private IdentityService identityService;
    @Mock
    private Authentication authentication;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private RiskGuardProperties properties;
    private OAuth2AuthenticationSuccessHandler handler;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        properties = new RiskGuardProperties();
        properties.getSecurity().setJwtExpirationMs(3600000L);
        properties.getSecurity().setCookieSecure(false);
        properties.getSecurity().setFrontendBaseUrl("http://localhost:3000");
        properties.getSecurity().setRefreshTokenExpirationDays(30);
        properties.getIdentity().setCookieName("auth_token");
        properties.getIdentity().setRefreshCookieName("refresh_token");
        AuthCookieHelper authCookieHelper = new AuthCookieHelper(properties);
        handler = new OAuth2AuthenticationSuccessHandler(tokenProvider, properties, identityService, authCookieHelper);
    }

    @Test
    void shouldAlwaysResetActiveTenantToHomeTenantOnLogin() throws IOException {
        // Given — a returning user whose home tenant is different from a hypothetical
        // previously-active tenant. The success handler should always issue a JWT
        // with active_tenant_id == home_tenant_id, regardless of prior session state.
        UUID userId = UUID.randomUUID();
        UUID homeTenantId = UUID.randomUUID();
        String email = "returning-user@test.com";
        String role = "ACCOUNTANT";

        OAuth2User delegate = new DefaultOAuth2User(
                Collections.emptyList(),
                Map.of("email", email, "name", "Test User", "sub", "sub-123"),
                "sub"
        );
        CustomOAuth2User oAuth2User = new CustomOAuth2User(delegate, userId, homeTenantId, role, "PRO");

        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(tokenProvider.createToken(anyString(), any(UUID.class), any(UUID.class), any(UUID.class), anyString(), anyString()))
                .thenReturn("jwt-token");
        when(identityService.issueRefreshToken(any(UUID.class), any(UUID.class)))
                .thenReturn("refresh-token");

        // When
        handler.onAuthenticationSuccess(request, response, authentication);

        // Then — active_tenant_id (4th arg) MUST equal home_tenant_id (3rd arg)
        ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> homeTenantCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> activeTenantCaptor = ArgumentCaptor.forClass(UUID.class);

        verify(tokenProvider).createToken(
                eq(email),
                userIdCaptor.capture(),
                homeTenantCaptor.capture(),
                activeTenantCaptor.capture(),
                eq(role),
                eq("PRO")
        );

        assertThat(homeTenantCaptor.getValue()).isEqualTo(homeTenantId);
        assertThat(activeTenantCaptor.getValue())
                .as("On SSO login, active_tenant_id must always reset to home_tenant_id")
                .isEqualTo(homeTenantId);
    }

    @Test
    void shouldRedirectToFrontendCallbackWithoutTokenInUrl() throws IOException {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String email = "user@test.com";

        OAuth2User delegate = new DefaultOAuth2User(
                Collections.emptyList(),
                Map.of("email", email, "name", "User", "sub", "sub-456"),
                "sub"
        );
        CustomOAuth2User oAuth2User = new CustomOAuth2User(delegate, userId, tenantId, "SME_ADMIN", "ALAP");

        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(tokenProvider.createToken(anyString(), any(UUID.class), any(UUID.class), any(UUID.class), anyString(), anyString()))
                .thenReturn("jwt-token");
        when(identityService.issueRefreshToken(any(UUID.class), any(UUID.class)))
                .thenReturn("refresh-token");

        // When
        handler.onAuthenticationSuccess(request, response, authentication);

        // Then — redirect target should be the callback path without token in URL
        String redirectUrl = response.getRedirectedUrl();
        assertThat(redirectUrl).isNotNull();
        assertThat(redirectUrl).contains("/login/callback");
        assertThat(redirectUrl).doesNotContain("token=");
    }
}
