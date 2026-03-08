package hu.riskguard.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.jackson2.CoreJackson2Module;
import org.springframework.security.oauth2.client.jackson2.OAuth2ClientJackson2Module;

import java.util.Base64;
import java.util.Optional;

@SuppressWarnings("removal") // CoreJackson2Module/OAuth2ClientJackson2Module: migrate to Jackson 3 (tools.jackson) modules when removing Jackson 2
public class CookieUtils {

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();
        // Register Spring Security Jackson mixins for correct serialization of
        // OAuth2AuthorizationRequest and related types. This ensures round-trip
        // fidelity across Spring Security upgrades.
        // TODO: Migrate to tools.jackson.databind.ObjectMapper + CoreJacksonModule / OAuth2ClientJacksonModule
        //       when fully migrating from Jackson 2 (com.fasterxml) to Jackson 3 (tools.jackson).
        OBJECT_MAPPER.registerModule(new CoreJackson2Module());
        OBJECT_MAPPER.registerModule(new OAuth2ClientJackson2Module());
    }

    public static Optional<Cookie> getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    return Optional.of(cookie);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Adds a cookie using ResponseCookie API with Secure and SameSite flags.
     * Secure flag is derived from the forceSecure parameter (should be true in production behind TLS-terminating proxies).
     */
    public static void addCookie(HttpServletResponse response, String name, String value, int maxAge, boolean forceSecure) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .path("/")
                .httpOnly(true)
                .secure(forceSecure)
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Overload for backward compatibility — defaults to secure=false for local dev.
     * Callers should migrate to the forceSecure variant.
     */
    public static void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        addCookie(response, name, value, maxAge, false);
    }

    /**
     * Deletes a cookie by setting maxAge=0 with matching Secure and SameSite attributes.
     * The deletion cookie MUST match the creation cookie's attributes — otherwise Chromium
     * may silently fail to delete it in production (behind TLS-terminating proxy).
     */
    public static void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name, boolean forceSecure) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    ResponseCookie deleteCookie = ResponseCookie.from(name, "")
                            .path("/")
                            .httpOnly(true)
                            .secure(forceSecure)
                            .sameSite("Lax")
                            .maxAge(0)
                            .build();
                    response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());
                }
            }
        }
    }

    /**
     * Overload for backward compatibility — defaults to secure=false for local dev.
     * Callers should migrate to the forceSecure variant.
     */
    public static void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        deleteCookie(request, response, name, false);
    }

    /**
     * Serializes an object to a Base64-encoded JSON string (safe for cookie storage).
     * Replaces the previous Java SerializationUtils approach which was vulnerable to RCE.
     */
    public static String serialize(Object object) {
        try {
            byte[] json = OBJECT_MAPPER.writeValueAsBytes(object);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize object to JSON for cookie", e);
        }
    }

    /**
     * Deserializes a Base64-encoded JSON cookie value back into an object.
     * Replaces the previous Java SerializationUtils.deserialize() which allowed arbitrary object instantiation (RCE).
     */
    public static <T> T deserialize(Cookie cookie, Class<T> cls) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(cookie.getValue());
            return OBJECT_MAPPER.readValue(json, cls);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize cookie value from JSON", e);
        }
    }
}
