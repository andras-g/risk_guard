package hu.riskguard.identity.api;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.security.TokenProvider;
import hu.riskguard.identity.api.dto.TenantResponse;
import hu.riskguard.identity.api.dto.TenantSwitchRequest;
import hu.riskguard.identity.api.dto.UpdateLanguageRequest;
import hu.riskguard.identity.api.dto.UserResponse;
import hu.riskguard.identity.domain.events.TenantContextSwitchedEvent;
import hu.riskguard.identity.domain.IdentityService;
import hu.riskguard.identity.domain.User;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/identity")
@RequiredArgsConstructor
public class IdentityController {

    private final IdentityService identityService;
    private final TokenProvider tokenProvider;
    private final RiskGuardProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        String email = jwt.getSubject();
        User user = identityService.findUserByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return UserResponse.from(user, jwt.getClaimAsString("active_tenant_id"));
    }

    @PatchMapping("/me/language")
    public ResponseEntity<Void> updateLanguage(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateLanguageRequest request) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        String email = jwt.getSubject();
        User user = identityService.findUserByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        identityService.updatePreferredLanguage(user.getId(), request.language());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mandates")
    public List<TenantResponse> getMandates(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        // Explicit role check — consistent with the pattern used in switchTenant.
        if (!"ACCOUNTANT".equals(jwt.getClaimAsString("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ACCOUNTANT role can access mandates");
        }
        String email = jwt.getSubject();
        User user = identityService.findUserByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return identityService.findMandatedTenants(user.getId());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        // Issue a Max-Age=0 deletion cookie to clear the HttpOnly auth_token.
        // The frontend cannot clear HttpOnly cookies via JavaScript — this endpoint
        // is the only way to properly terminate the session.
        ResponseCookie deletionCookie = ResponseCookie.from(properties.getIdentity().getCookieName(), "")
                .path("/")
                .maxAge(0)
                .secure(properties.getSecurity().isCookieSecure())
                .httpOnly(true)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, deletionCookie.toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tenants/switch")
    @Transactional
    public ResponseEntity<Void> switchTenant(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TenantSwitchRequest request,
            HttpServletResponse response) {

        String email = jwt.getSubject();
        User user = identityService.findUserByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Authorization logic:
        // - Any user can switch back to their own home tenant (self-switch — SME_ADMIN and ACCOUNTANT).
        // - Only ACCOUNTANT users can switch to a different (mandated) tenant.
        boolean isSelfSwitch = user.getTenantId().equals(request.tenantId());
        if (!isSelfSwitch) {
            // Non-ACCOUNTANT roles cannot switch to external tenants
            if (!"ACCOUNTANT".equals(user.getRole())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ACCOUNTANT role can switch to external tenants");
            }
            if (!identityService.hasMandate(user.getId(), request.tenantId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No mandate for this tenant");
            }
        }

        // Publish audit event BEFORE response commit — if event publication fails,
        // the exception propagates and the tenant switch is aborted (no silent audit loss).
        // Email is intentionally excluded from the event per PII zero-tolerance policy.
        UUID previousTenantId = UUID.fromString(jwt.getClaimAsString("active_tenant_id"));
        eventPublisher.publishEvent(
                TenantContextSwitchedEvent.of(user.getId(), previousTenantId, request.tenantId())
        );

        // Issue token — set as HttpOnly cookie ONLY (not in response body to prevent token leaking)
        String tier = identityService.findTenantTier(request.tenantId());
        String newToken = tokenProvider.createToken(email, user.getId(), user.getTenantId(), request.tenantId(), user.getRole(),
                tier != null ? tier : properties.getIdentity().getDefaultTier());

        ResponseCookie cookie = ResponseCookie.from(properties.getIdentity().getCookieName(), newToken)
                .path("/")
                .maxAge(properties.getSecurity().getJwtExpirationMs() / 1000)
                .secure(properties.getSecurity().isCookieSecure())
                .httpOnly(true)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.noContent().build();
    }
}
