package hu.riskguard.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.util.Base64;
import java.util.Optional;

public class CookieUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    public static void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    ResponseCookie deleteCookie = ResponseCookie.from(name, "")
                            .path("/")
                            .httpOnly(true)
                            .maxAge(0)
                            .build();
                    response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());
                }
            }
        }
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
