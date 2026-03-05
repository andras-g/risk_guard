package hu.riskguard.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TenantFilterTest {

    private TenantFilter tenantFilter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        tenantFilter = new TenantFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        MDC.clear();
    }

    @Test
    void shouldPopulateTenantContextWhenJwtPresent() throws ServletException, IOException {
        // Given
        UUID tenantId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("active_tenant_id", tenantId.toString())
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // When
        tenantFilter.doFilter(request, response, filterChain);

        // Then
        assertEquals(tenantId, TenantContext.getCurrentTenant());
        assertEquals(tenantId.toString(), MDC.get("tenantId"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotPopulateTenantContextWhenJwtMissing() throws ServletException, IOException {
        // When
        tenantFilter.doFilter(request, response, filterChain);

        // Then
        assertNull(TenantContext.getCurrentTenant());
        assertNull(MDC.get("tenantId"));
        verify(filterChain).doFilter(request, response);
    }
}
