package hu.riskguard.identity.api;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.security.TokenProvider;
import hu.riskguard.identity.api.dto.TenantSwitchRequest;
import hu.riskguard.identity.api.dto.TenantSwitchResponse;
import hu.riskguard.identity.api.dto.UserResponse;
import hu.riskguard.identity.domain.User;
import hu.riskguard.identity.internal.IdentityRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/identity")
@RequiredArgsConstructor
public class IdentityController {

    private final IdentityRepository identityRepository;
    private final TokenProvider tokenProvider;
    private final RiskGuardProperties properties;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getSubject();
        User user = identityRepository.findUserByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return UserResponse.from(user, jwt.getClaimAsString("active_tenant_id"));
    }

    @PostMapping("/tenants/switch")
    public TenantSwitchResponse switchTenant(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody TenantSwitchRequest request,
            HttpServletResponse response) {
        
        String email = jwt.getSubject();
        User user = identityRepository.findUserByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Verify that the user has a mandate for the requested tenant or it is their home tenant
        if (!user.getTenantId().equals(request.tenantId()) && !identityRepository.hasMandate(user.getId(), request.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No mandate for this tenant");
        }

        // Issue a new token with the new active_tenant_id
        String newToken = tokenProvider.createToken(email, user.getTenantId(), request.tenantId());
        
        ResponseCookie cookie = ResponseCookie.from(properties.getIdentity().getCookieName(), newToken)
                .path("/")
                .maxAge(3600)
                .secure(true) // Should be based on request.isSecure() in real handler, but here we enforce secure
                .httpOnly(true)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return new TenantSwitchResponse(newToken);
    }
}
