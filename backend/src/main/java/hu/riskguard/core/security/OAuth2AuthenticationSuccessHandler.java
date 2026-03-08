package hu.riskguard.core.security;

import hu.riskguard.core.config.RiskGuardProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final TokenProvider tokenProvider;
    private final RiskGuardProperties properties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        String email = (String) oAuth2User.getAttribute("email");

        // Intentional: active_tenant_id is always reset to home_tenant_id on SSO login.
        // Returning users who had previously switched tenants start fresh in their home
        // context. They can explicitly switch again via the Context Switcher UI.
        // This prevents stale tenant contexts from persisting across sessions.
        String token = tokenProvider.createToken(email, oAuth2User.getTenantId(), oAuth2User.getTenantId(), oAuth2User.getRole());

        ResponseCookie cookie = ResponseCookie.from(properties.getIdentity().getCookieName(), token)
                .path("/")
                .maxAge(properties.getSecurity().getJwtExpirationMs() / 1000)
                .secure(properties.getSecurity().isCookieSecure())
                .httpOnly(true)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // Redirect WITHOUT token in query param — the HttpOnly cookie set above is sufficient.
        // Previously, the token was appended as ?token=... which leaked it via browser history,
        // server access logs, and Referer headers.
        String targetUrl = properties.getSecurity().getFrontendBaseUrl() + "/login/callback";
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
