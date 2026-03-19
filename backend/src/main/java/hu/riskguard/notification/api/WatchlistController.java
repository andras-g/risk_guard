package hu.riskguard.notification.api;

import hu.riskguard.notification.api.dto.AddWatchlistEntryRequest;
import hu.riskguard.notification.api.dto.AddWatchlistEntryResponse;
import hu.riskguard.notification.api.dto.WatchlistCountResponse;
import hu.riskguard.notification.api.dto.WatchlistEntryResponse;
import hu.riskguard.notification.domain.NotificationService;
import hu.riskguard.notification.domain.NotificationService.AddResult;
import hu.riskguard.notification.domain.WatchlistEntry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for watchlist CRUD operations.
 * Delegates business logic to {@link NotificationService} facade.
 *
 * <p>Verdict enrichment (currentVerdictStatus, lastCheckedAt) is provided via denormalized
 * columns on {@code watchlist_entries}. These columns are populated by:
 * <ul>
 *   <li>The {@code PartnerStatusChangedListener} — reacts to verdict changes from
 *       user-initiated searches and the 24h background monitoring cycle</li>
 *   <li>The {@code addToWatchlist} flow — sets initial verdict status at insert time</li>
 * </ul>
 *
 * <p>Tenant identity extracted from JWT claims — no cross-module dependency on the identity module.
 */
@RestController
@RequestMapping("/api/v1/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final NotificationService notificationService;

    /**
     * Add a partner to the current tenant's watchlist.
     * Duplicate tax numbers return the existing entry with {@code duplicate=true} (idempotent).
     */
    @PostMapping
    public AddWatchlistEntryResponse addEntry(
            @Valid @RequestBody AddWatchlistEntryRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = requireUuidClaim(jwt, "active_tenant_id");
        AddResult result = notificationService.addToWatchlist(
                tenantId, request.taxNumber(), request.companyName(), request.verdictStatus());
        return AddWatchlistEntryResponse.from(result);
    }

    /**
     * List all watchlist entries for the current tenant.
     */
    @GetMapping
    public List<WatchlistEntryResponse> listEntries(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = requireUuidClaim(jwt, "active_tenant_id");
        List<WatchlistEntry> entries = notificationService.getWatchlistEntries(tenantId);
        return entries.stream()
                .map(WatchlistEntryResponse::from)
                .toList();
    }

    /**
     * Remove a watchlist entry by ID. Verifies tenant ownership.
     * Returns 404 (not 403) if entry not found or not owned — prevents info leakage.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeEntry(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = requireUuidClaim(jwt, "active_tenant_id");
        boolean deleted = notificationService.removeFromWatchlist(tenantId, id);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist entry not found");
        }
    }

    /**
     * Get watchlist entry count for the sidebar badge.
     */
    @GetMapping("/count")
    public WatchlistCountResponse getCount(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = requireUuidClaim(jwt, "active_tenant_id");
        int count = notificationService.getWatchlistCount(tenantId);
        return WatchlistCountResponse.from(count);
    }

    /**
     * Extract and validate a UUID claim from the JWT.
     */
    private UUID requireUuidClaim(Jwt jwt, String claimName) {
        String claimValue = jwt.getClaimAsString(claimName);
        if (claimValue == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing " + claimName + " claim in JWT");
        }
        try {
            return UUID.fromString(claimValue);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid " + claimName + " claim in JWT: not a valid UUID");
        }
    }
}
