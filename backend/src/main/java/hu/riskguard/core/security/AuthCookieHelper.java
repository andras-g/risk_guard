package hu.riskguard.core.security;

import hu.riskguard.core.config.RiskGuardProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Shared utility for setting and clearing auth cookies (access + refresh).
 * Eliminates duplication across AuthController, OAuth2AuthenticationSuccessHandler,
 * and IdentityController.
 *
 * <p>Both cookies use identical flags: HttpOnly, Secure (profile-dependent),
 * SameSite=Lax, Path=/. Only maxAge differs (15-min for access, 30-day for refresh).
 */
@Component
@RequiredArgsConstructor
public class AuthCookieHelper {

    private final RiskGuardProperties properties;

    /**
     * Set both auth_token and refresh_token HttpOnly cookies on the response.
     */
    public void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        ResponseCookie accessCookie = ResponseCookie.from(properties.getIdentity().getCookieName(), accessToken)
                .path("/")
                .maxAge(properties.getSecurity().getJwtExpirationMs() / 1000)
                .secure(properties.getSecurity().isCookieSecure())
                .httpOnly(true)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());

        ResponseCookie refreshCookie = ResponseCookie.from(properties.getIdentity().getRefreshCookieName(), refreshToken)
                .path("/")
                .maxAge(properties.getSecurity().getRefreshTokenExpirationDays() * 24 * 60 * 60)
                .secure(properties.getSecurity().isCookieSecure())
                .httpOnly(true)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    /**
     * Extract a cookie value from the request by name. Returns null if not found.
     */
    public String extractCookie(jakarta.servlet.http.HttpServletRequest request, String cookieName) {
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Clear both auth cookies by issuing Max-Age=0 deletion cookies.
     */
    public void clearAuthCookies(HttpServletResponse response) {
        ResponseCookie accessDeletion = ResponseCookie.from(properties.getIdentity().getCookieName(), "")
                .path("/")
                .maxAge(0)
                .secure(properties.getSecurity().isCookieSecure())
                .httpOnly(true)
                .sameSite("Lax")
                .build();
        ResponseCookie refreshDeletion = ResponseCookie.from(properties.getIdentity().getRefreshCookieName(), "")
                .path("/")
                .maxAge(0)
                .secure(properties.getSecurity().isCookieSecure())
                .httpOnly(true)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessDeletion.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshDeletion.toString());
    }
}
