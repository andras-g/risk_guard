package hu.riskguard.screening.api;

import hu.riskguard.screening.api.dto.PartnerSearchRequest;
import hu.riskguard.screening.api.dto.ProvenanceResponse;
import hu.riskguard.screening.api.dto.VerdictResponse;
import hu.riskguard.screening.domain.ScreeningService;
import hu.riskguard.screening.domain.ScreeningService.SearchResult;
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
     *
     * <p>Creates a company snapshot, scrapes government data in parallel, computes a deterministic
     * verdict via {@code VerdictEngine} (RELIABLE / AT_RISK / INCOMPLETE / TAX_SUSPENDED),
     * and writes a SHA-256–signed audit log entry.
     *
     * @param request the search request containing the tax number
     * @param jwt     the authenticated user's JWT token
     * @return VerdictResponse with the computed verdict (status, confidence, riskSignals)
     */
    @PostMapping("/search")
    public VerdictResponse search(
            @Valid @RequestBody PartnerSearchRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = requireUuidClaim(jwt, "user_id");
        UUID tenantId = requireUuidClaim(jwt, "active_tenant_id");

        SearchResult result = screeningService.search(request.taxNumber(), userId, tenantId);

        return VerdictResponse.from(result);
    }

    /**
     * Get provenance data for a snapshot — per-source availability details
     * used by the Provenance Sidebar in the Verdict Detail page.
     *
     * @param snapshotId the snapshot UUID (from VerdictResponse.snapshotId)
     * @param jwt        the authenticated user's JWT token
     * @return ProvenanceResponse with per-source availability details
     * @throws ResponseStatusException 404 if snapshot not found or not owned by the tenant
     */
    @GetMapping("/snapshots/{snapshotId}/provenance")
    public ProvenanceResponse getSnapshotProvenance(
            @PathVariable UUID snapshotId,
            @AuthenticationPrincipal Jwt jwt) {

        // Validate JWT is present (tenant isolation is enforced by TenantContext in repository layer)
        requireUuidClaim(jwt, "active_tenant_id");

        return screeningService.getSnapshotProvenance(snapshotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Snapshot not found or not accessible"));
    }

    /**
     * Extract and validate a UUID claim from the JWT. Throws 401 if the claim is absent
     * or not a valid UUID. Named {@code requireUuidClaim} to make the side-effect explicit
     * at call sites where the return value is intentionally discarded (validation-only use).
     */
    private UUID requireUuidClaim(Jwt jwt, String claimName) {
        String claimValue = jwt.getClaimAsString(claimName);
        if (claimValue == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing " + claimName + " claim in JWT");
        }
        try {
            return UUID.fromString(claimValue);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid " + claimName + " claim in JWT: not a valid UUID");
        }
    }
}
