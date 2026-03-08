package hu.riskguard.core.security;

import hu.riskguard.core.config.RiskGuardProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that OAuth2AuthenticationFailureHandler redirects to the frontend
 * login page with a plain i18n-compatible error key (not a URN).
 * Uses Spring MockHttpServlet* to support DefaultRedirectStrategy's encodeRedirectURL call.
 */
class OAuth2AuthenticationFailureHandlerTest {

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private OAuth2AuthenticationFailureHandler handler;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        RiskGuardProperties properties = new RiskGuardProperties();
        properties.getSecurity().setFrontendBaseUrl("http://localhost:3000");
        handler = new OAuth2AuthenticationFailureHandler(properties);
    }

    @Test
    void shouldRedirectToLoginWithI18nCompatibleErrorKey() throws IOException {
        // Given
        AuthenticationException exception = new OAuth2AuthenticationException("provider_error");

        // When
        handler.onAuthenticationFailure(request, response, exception);

        // Then — verify redirect was sent with error=auth-failed (not a URN)
        String redirectUrl = response.getRedirectedUrl();
        assertThat(redirectUrl).isNotNull();
        assertThat(redirectUrl).startsWith("http://localhost:3000/auth/login");
        assertThat(redirectUrl).contains("error=auth-failed");
        // Must NOT contain URN prefix — frontend expects a plain key for i18n lookup
        assertThat(redirectUrl).doesNotContain("urn:");
    }

    @Test
    void shouldUseConfiguredFrontendBaseUrl() throws IOException {
        // Given — different base URL
        RiskGuardProperties customProperties = new RiskGuardProperties();
        customProperties.getSecurity().setFrontendBaseUrl("https://app.riskguard.hu");
        OAuth2AuthenticationFailureHandler customHandler = new OAuth2AuthenticationFailureHandler(customProperties);

        AuthenticationException exception = new OAuth2AuthenticationException("any_error");

        // When
        customHandler.onAuthenticationFailure(request, response, exception);

        // Then
        String redirectUrl = response.getRedirectedUrl();
        assertThat(redirectUrl).isNotNull();
        assertThat(redirectUrl).startsWith("https://app.riskguard.hu/auth/login");
    }
}
