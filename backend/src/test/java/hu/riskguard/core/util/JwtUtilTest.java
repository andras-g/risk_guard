package hu.riskguard.core.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtUtilTest {

    @Test
    void requireUuidClaim_happyPath_returnsUuid() {
        UUID expected = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("user_id")).thenReturn(expected.toString());

        UUID result = JwtUtil.requireUuidClaim(jwt, "user_id");

        assertEquals(expected, result);
    }

    @Test
    void requireUuidClaim_nullClaim_throws401() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("user_id")).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> JwtUtil.requireUuidClaim(jwt, "user_id"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Missing user_id claim in JWT"));
    }

    @Test
    void requireUuidClaim_malformedUuid_throws401() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("user_id")).thenReturn("not-a-uuid");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> JwtUtil.requireUuidClaim(jwt, "user_id"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Invalid user_id claim in JWT"));
    }
}
