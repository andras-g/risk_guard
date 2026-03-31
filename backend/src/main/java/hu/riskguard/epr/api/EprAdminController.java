package hu.riskguard.epr.api;

import hu.riskguard.core.util.JwtUtil;
import hu.riskguard.epr.api.dto.EprConfigPublishRequest;
import hu.riskguard.epr.api.dto.EprConfigPublishResponse;
import hu.riskguard.epr.api.dto.EprConfigResponse;
import hu.riskguard.epr.api.dto.EprConfigValidateRequest;
import hu.riskguard.epr.api.dto.EprConfigValidateResponse;
import hu.riskguard.epr.domain.EprService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Admin REST controller for hot-swappable EPR config management.
 * Restricted to {@code SME_ADMIN} role only.
 */
@RestController
@RequestMapping("/api/v1/admin/epr")
@RequiredArgsConstructor
public class EprAdminController {

    private final EprService eprService;

    /**
     * Returns the currently active EPR config (version, JSON data, activation timestamp).
     * Requires {@code SME_ADMIN} role; returns 403 for all other roles.
     */
    @GetMapping("/config")
    public EprConfigResponse getConfig(@AuthenticationPrincipal Jwt jwt) {
        requireAdminRole(jwt);
        return eprService.getActiveConfigFull();
    }

    /**
     * Validates the provided EPR config JSON against the 5 golden test cases.
     * Requires {@code SME_ADMIN} role; returns 403 for all other roles.
     */
    @PostMapping("/config/validate")
    public EprConfigValidateResponse validate(
            @Valid @RequestBody EprConfigValidateRequest req,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireAdminRole(jwt);
        return eprService.validateNewConfig(req.configData());
    }

    /**
     * Publishes the provided EPR config JSON as a new active version and logs the action.
     * Requires {@code SME_ADMIN} role; returns 403 for all other roles.
     */
    @PostMapping("/config/publish")
    public EprConfigPublishResponse publish(
            @Valid @RequestBody EprConfigPublishRequest req,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireAdminRole(jwt);
        UUID actorUserId = JwtUtil.requireUuidClaim(jwt, "user_id");
        return eprService.publishNewConfig(req.configData(), actorUserId);
    }

    private void requireAdminRole(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        if (!"SME_ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }
}
