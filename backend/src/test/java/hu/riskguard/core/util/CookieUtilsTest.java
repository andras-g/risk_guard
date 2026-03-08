package hu.riskguard.core.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CookieUtilsTest {

    @Test
    void shouldRoundTripOAuth2AuthorizationRequest() {
        // Given: a typical OAuth2 authorization request
        OAuth2AuthorizationRequest original = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2.0/auth")
                .clientId("test-client-id")
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .scope("email", "profile")
                .state("random-state-value")
                .build();

        // When: serialize and then deserialize
        String serialized = CookieUtils.serialize(original);
        Cookie cookie = new Cookie("test", serialized);
        OAuth2AuthorizationRequest deserialized = CookieUtils.deserialize(cookie, OAuth2AuthorizationRequest.class);

        // Then: all fields should round-trip correctly
        assertThat(deserialized.getAuthorizationUri()).isEqualTo(original.getAuthorizationUri());
        assertThat(deserialized.getClientId()).isEqualTo(original.getClientId());
        assertThat(deserialized.getRedirectUri()).isEqualTo(original.getRedirectUri());
        assertThat(deserialized.getScopes()).containsExactlyInAnyOrderElementsOf(original.getScopes());
        assertThat(deserialized.getState()).isEqualTo(original.getState());
        assertThat(deserialized.getGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
    }

    @Test
    void shouldDeleteCookieWithSecureAndSameSiteAttributes() {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        Cookie cookie = new Cookie("oauth2_auth_request", "value");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});

        // When — delete with forceSecure=true
        CookieUtils.deleteCookie(request, response, "oauth2_auth_request", true);

        // Then — deletion cookie must include Secure and SameSite=Lax to match creation attributes
        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(org.mockito.ArgumentMatchers.eq(HttpHeaders.SET_COOKIE), headerCaptor.capture());

        String setCookieHeader = headerCaptor.getValue();
        assertThat(setCookieHeader).contains("Max-Age=0");
        assertThat(setCookieHeader).contains("Secure");
        assertThat(setCookieHeader).contains("SameSite=Lax");
        assertThat(setCookieHeader).contains("HttpOnly");
    }

    @Test
    void shouldDeleteCookieWithoutSecureForLocalDev() {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        Cookie cookie = new Cookie("auth_token", "value");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});

        // When — delete with forceSecure=false (local dev)
        CookieUtils.deleteCookie(request, response, "auth_token", false);

        // Then — no Secure attribute, but still SameSite=Lax
        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(org.mockito.ArgumentMatchers.eq(HttpHeaders.SET_COOKIE), headerCaptor.capture());

        String setCookieHeader = headerCaptor.getValue();
        assertThat(setCookieHeader).contains("Max-Age=0");
        assertThat(setCookieHeader).contains("SameSite=Lax");
        assertThat(setCookieHeader).doesNotContain("Secure");
    }

    @Test
    void shouldSerializeSimpleObject() {
        // Given: a simple string value
        String original = "test-value";

        // When: serialize
        String serialized = CookieUtils.serialize(original);

        // Then: should produce a non-empty Base64 string
        assertThat(serialized).isNotBlank();

        // And: deserialize should produce the original value
        Cookie cookie = new Cookie("test", serialized);
        String deserialized = CookieUtils.deserialize(cookie, String.class);
        assertThat(deserialized).isEqualTo(original);
    }
}
