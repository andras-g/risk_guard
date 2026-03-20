package hu.riskguard.screening.api;

import hu.riskguard.core.security.TenantContext;
import hu.riskguard.identity.domain.GuestLimitStatus;
import hu.riskguard.identity.domain.GuestSession;
import hu.riskguard.identity.domain.IdentityService;
import hu.riskguard.screening.api.dto.GuestLimitResponse;
import hu.riskguard.screening.api.dto.GuestSearchRequest;
import hu.riskguard.screening.api.dto.GuestSearchResponse;
import hu.riskguard.screening.domain.ScreeningService;
import hu.riskguard.screening.domain.ScreeningService.SearchResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public (unauthenticated) REST controller for guest demo searches.
 * Allows guests to try the product with limited access before signing up.
 *
 * <p>Permitted via {@code /api/v1/public/**} in SecurityConfig.
 * No {@code @AuthenticationPrincipal} — this is intentionally unauthenticated.
 *
 * <p>Calls the identity facade for session management and the screening facade for search.
 * Manually sets TenantContext with the synthetic guest tenant ID before calling
 * ScreeningService, and clears it in the finally block.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public/guest")
@RequiredArgsConstructor
public class GuestSearchController {

    private final IdentityService identityService;
    private final ScreeningService screeningService;

    /**
     * Perform a guest (unauthenticated) partner search.
     *
     * <p>Flow:
     * <ol>
     *   <li>Find or create guest session by fingerprint (with row-level lock)</li>
     *   <li>Check rate limits (company limit, daily limit)</li>
     *   <li>Set synthetic tenant context</li>
     *   <li>Check if this is a new company (via screening facade)</li>
     *   <li>Call screening service</li>
     *   <li>Increment counters</li>
     *   <li>Return verdict + usage stats</li>
     * </ol>
     *
     * @param request the guest search request (tax number + session fingerprint)
     * @return 200 with verdict + usage stats, or 429 if rate limited
     */
    @PostMapping("/search")
    public ResponseEntity<?> guestSearch(@Valid @RequestBody GuestSearchRequest request) {
        // Step 1: Find or create guest session (SELECT ... FOR UPDATE prevents TOCTOU race)
        GuestSession session = identityService.findOrCreateGuestSession(request.sessionFingerprint());

        // Step 2: Check rate limits
        GuestLimitStatus limitStatus = identityService.checkGuestLimits(session);

        if (limitStatus == GuestLimitStatus.COMPANY_LIMIT_REACHED) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(GuestLimitResponse.companyLimitReached(
                            session.companiesChecked(),
                            identityService.getGuestMaxCompanies()));
        }

        if (limitStatus == GuestLimitStatus.DAILY_LIMIT_REACHED) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(GuestLimitResponse.dailyLimitReached(
                            session.dailyChecks(),
                            identityService.getGuestMaxDailyChecks()));
        }

        // Step 3: Set synthetic tenant context for screening operations
        UUID syntheticTenantId = session.tenantId();
        String normalizedTaxNumber = request.taxNumber().replaceAll("[\\s-]", "");

        boolean isNewCompany;
        SearchResult result;

        TenantContext.setCurrentTenant(syntheticTenantId);
        try {
            // Step 4: Check if this is a new company (via screening module — owns company_snapshots)
            isNewCompany = !screeningService.hasSnapshotForTenant(
                    syntheticTenantId, normalizedTaxNumber);

            // Re-check company limit if this would be a new company
            if (isNewCompany && session.companiesChecked() >= identityService.getGuestMaxCompanies()) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(GuestLimitResponse.companyLimitReached(
                                session.companiesChecked(),
                                identityService.getGuestMaxCompanies()));
            }

            // Step 5: Perform search (requires TenantContext for snapshot/verdict creation)
            UUID guestUserId = new UUID(0, 0);
            result = screeningService.search(
                    request.taxNumber(), guestUserId, syntheticTenantId);
        } finally {
            TenantContext.clear();
        }

        // Step 6: Increment counters after successful search (outside TenantContext —
        // uses session ID only, not tenant context; identity module owns guest_sessions)
        identityService.incrementGuestCounters(session.id(), isNewCompany);

        // Step 7: Calculate updated usage stats
        int updatedCompaniesUsed = session.companiesChecked() + (isNewCompany ? 1 : 0);
        int updatedDailyChecksUsed = session.dailyChecks() + 1;

        return ResponseEntity.ok(GuestSearchResponse.from(
                result,
                updatedCompaniesUsed,
                identityService.getGuestMaxCompanies(),
                updatedDailyChecksUsed,
                identityService.getGuestMaxDailyChecks()
        ));
    }
}
