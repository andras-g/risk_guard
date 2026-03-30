package hu.riskguard.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);
    private static final String TENANT_ID_CLAIM = "active_tenant_id";
    private static final String MDC_TENANT_ID = "tenantId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String tenantIdStr = jwt.getClaimAsString(TENANT_ID_CLAIM);
            if (tenantIdStr != null) {
                UUID tenantId = UUID.fromString(tenantIdStr);
                MDC.put(MDC_TENANT_ID, tenantIdStr);
                try {
                    ScopedValue.where(TenantContext.CURRENT_TENANT, tenantId)
                            .call(() -> { filterChain.doFilter(request, response); return null; });
                } catch (IOException | ServletException e) {
                    throw e;
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    MDC.remove(MDC_TENANT_ID);
                }
                return;
            } else {
                // Authenticated user without active_tenant_id — this is a security concern.
                // Log it so we can track requests that bypass tenant context.
                log.warn("Authenticated request without active_tenant_id claim. Subject: {}, URI: {}",
                        jwt.getSubject(), request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }
}
