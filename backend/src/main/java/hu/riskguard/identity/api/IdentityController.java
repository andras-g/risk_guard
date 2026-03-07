package hu.riskguard.identity.api;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.security.TokenProvider;
import hu.riskguard.identity.api.dto.TenantResponse;
import hu.riskguard.identity.api.dto.TenantSwitchRequest;
import hu.riskguard.identity.api.dto.UserResponse;
import hu.riskguard.identity.domain.IdentityService;
import hu.riskguard.identity.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/identity")
@RequiredArgsConstructor
public class IdentityController {

    private final IdentityService identityService;
    private final TokenProvider tokenProvider;
    private final RiskGuardProperties properties;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getSubject();
        User user = identityService.findUserByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return UserResponse.from(user, jwt.getClaimAsString("active_tenant_id"));
    }

    @GetMapping("/mandates")
    public List<TenantResponse> getMandates(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getSubject();
        User user = identityService.findUserByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return identityService.findMandatedTenants(user.getId());
    }

    @PostMapping("/tenants/switch")
    public void switchTenant(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TenantSwitchRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        
        String email = jwt.getSubject();
        User user = identityService.findUserByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Verify mandate
        if (!user.getTenantId().equals(request.tenantId()) && !identityService.hasMandate(user.getId(), request.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No mandate for this tenant");
        }

        // Issue token — set as HttpOnly cookie ONLY (not in response body to prevent token leaking)
        String newToken = tokenProvider.createToken(email, user.getTenantId(), request.tenantId());
        
        ResponseCookie cookie = ResponseCookie.from(properties.getIdentity().getCookieName(), newToken)
                .path("/")
                .maxAge(properties.getSecurity().getJwtExpirationMs() / 1000)
                .secure(properties.getSecurity().isCookieSecure())
                .httpOnly(true)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        response.setStatus(HttpStatus.NO_CONTENT.value());
    }
}
