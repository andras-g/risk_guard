package hu.riskguard.notification.api;

import hu.riskguard.notification.api.dto.FlightControlResponse;
import hu.riskguard.notification.api.dto.PortfolioAlertResponse;
import hu.riskguard.notification.domain.NotificationService;
import hu.riskguard.notification.domain.PortfolioAlert;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the accountant's cross-tenant Portfolio Pulse feed.
 * Returns recent partner status change events across ALL tenants the accountant
 * has active mandates for.
 *
 * <p>This is the FIRST endpoint that reads across multiple tenants for a single user.
 * Authorization is via mandate check (not TenantFilter). The accountant's user ID
 * is resolved from the JWT {@code sub} claim (email) via {@link NotificationService},
 * which delegates to the identity module internally.
 *
 * <p>Only users with the {@code ACCOUNTANT} role may access this endpoint.
 * Non-accountants receive {@code 403 FORBIDDEN}.
 */
@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final NotificationService notificationService;

    /**
     * Get recent portfolio alerts across all mandated tenants.
     *
     * @param days number of days to look back (default 7, min 1, max 30)
     * @param jwt  the authenticated user's JWT
     * @return list of portfolio alert DTOs, ordered by Morning Risk Pulse priority
     */
    @GetMapping("/alerts")
    public List<PortfolioAlertResponse> getAlerts(
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal Jwt jwt) {

        requireAccountantRole(jwt);
        validateDaysParameter(days);
        UUID userId = resolveUserId(jwt);

        List<PortfolioAlert> alerts = notificationService.getPortfolioAlerts(userId, days);
        return alerts.stream()
                .map(PortfolioAlertResponse::from)
                .toList();
    }

    /**
     * Get the accountant's cross-tenant Flight Control dashboard summary.
     *
     * <p>Returns per-client verdict status counts and portfolio-wide totals.
     * Tenants are ordered by atRiskCount DESC, then staleCount DESC.
     * Only users with the {@code ACCOUNTANT} role may access this endpoint.
     *
     * @param jwt the authenticated user's JWT
     * @return flight control response with totals and per-tenant summaries
     */
    @GetMapping("/flight-control")
    public FlightControlResponse getFlightControl(@AuthenticationPrincipal Jwt jwt) {
        requireAccountantRole(jwt);
        UUID userId = resolveUserId(jwt);

        NotificationService.FlightControlResult result = notificationService.getFlightControlSummary(userId);
        return FlightControlResponse.from(result);
    }

    /**
     * Verify the JWT holder has the ACCOUNTANT role.
     * Non-accountants are rejected with 403 FORBIDDEN.
     */
    private void requireAccountantRole(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        if (!"ACCOUNTANT".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This endpoint is only available to ACCOUNTANT users");
        }
    }

    /**
     * Validate the days query parameter — must be between 1 and 30 (inclusive).
     * Returns 400 BAD_REQUEST for out-of-range values.
     */
    private void validateDaysParameter(int days) {
        if (days < 1 || days > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "days parameter must be between 1 and 30");
        }
    }

    /**
     * Resolve the accountant's user UUID from the JWT {@code sub} claim (email).
     * Delegates to NotificationService facade to avoid cross-module identity imports.
     */
    private UUID resolveUserId(Jwt jwt) {
        String email = jwt.getSubject();
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing sub claim in JWT");
        }
        try {
            return notificationService.resolveUserIdByEmail(email);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "User not found for email in JWT sub claim");
        }
    }
}
