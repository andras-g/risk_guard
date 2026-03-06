package hu.riskguard.identity.api;

import hu.riskguard.core.security.TokenProvider;
import hu.riskguard.identity.api.dto.TenantSwitchRequest;
import hu.riskguard.identity.api.dto.TenantSwitchResponse;
import hu.riskguard.identity.domain.TenantMandateRepository;
import hu.riskguard.identity.domain.User;
import hu.riskguard.identity.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/identity")
@RequiredArgsConstructor
public class IdentityController {

    private final UserRepository userRepository;
    private final TenantMandateRepository mandateRepository;
    private final TokenProvider tokenProvider;

    @PostMapping("/tenants/switch")
    public TenantSwitchResponse switchTenant(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody TenantSwitchRequest request) {
        
        String email = jwt.getSubject();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Verify that the user has a mandate for the requested tenant
        if (!mandateRepository.existsByAccountantUserIdAndTenantId(user.getId(), request.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No mandate for this tenant");
        }

        // Issue a new token with the new active_tenant_id
        String newToken = tokenProvider.createToken(email, user.getTenantId(), request.tenantId());
        
        return new TenantSwitchResponse(newToken);
    }
}
