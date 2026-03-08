package hu.riskguard.core.security;

import hu.riskguard.core.config.RiskGuardProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final RiskGuardProperties properties;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        // Send a plain i18n-compatible error key (not a URN) so the frontend can look it up
        // directly as auth.login.error.${errorKey} without format conversion.
        String targetUrl = UriComponentsBuilder.fromUriString(properties.getSecurity().getFrontendBaseUrl() + "/auth/login")
                .queryParam("error", "auth-failed")
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}