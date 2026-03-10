package hu.riskguard.screening.api;

import hu.riskguard.core.security.TenantContext;
import hu.riskguard.screening.api.dto.PartnerSearchRequest;
import hu.riskguard.screening.api.dto.VerdictResponse;
import hu.riskguard.screening.domain.ScreeningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * REST controller for partner screening operations.
 * Delegates all business logic to {@link ScreeningService} facade.
 *
 * <p>User and tenant identity are extracted from JWT claims ({@code user_id},
 * {@code active_tenant_id}) — no cross-module dependency on the identity module.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/screenings")
@RequiredArgsConstructor
public class ScreeningController {

    private final ScreeningService screeningService;

    /**
     * Search for a partner by Hungarian tax number.
     * Creates a company snapshot, an initial verdict (INCOMPLETE), and an audit log entry.
     *
     * @param request the search request containing the tax number
     * @param jwt     the authenticated user's JWT token
     * @return VerdictResponse with the initial search result
     */
    @PostMapping("/search")
    public VerdictResponse search(
            @Valid @RequestBody PartnerSearchRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = extractUserId(jwt);
        UUID tenantId = extractTenantId(jwt);

        return screeningService.search(request.taxNumber(), userId, tenantId);
    }

    private UUID extractUserId(Jwt jwt) {
        String userIdClaim = jwt.getClaimAsString("user_id");
        if (userIdClaim == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user_id claim in JWT");
        }
        return UUID.fromString(userIdClaim);
    }

    private UUID extractTenantId(Jwt jwt) {
        String tenantIdClaim = jwt.getClaimAsString("active_tenant_id");
        if (tenantIdClaim == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing active_tenant_id claim in JWT");
        }
        return UUID.fromString(tenantIdClaim);
    }
}
