package hu.riskguard.epr.registry.api;

import hu.riskguard.core.security.Tier;
import hu.riskguard.core.security.TierRequired;
import hu.riskguard.core.util.JwtUtil;
import hu.riskguard.epr.registry.api.dto.*;
import hu.riskguard.epr.registry.domain.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST controller for NAV-invoice-driven registry bootstrap operations.
 *
 * <p>All endpoints require PRO_EPR tier. Tenant identity is extracted from JWT claims,
 * mirroring the pattern in {@link RegistryController}.
 */
@RestController
@RequestMapping("/api/v1/registry/bootstrap")
@RequiredArgsConstructor
@TierRequired(Tier.PRO_EPR)
public class RegistryBootstrapController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 50;

    private final RegistryBootstrapService bootstrapService;

    /**
     * Trigger NAV invoice ingestion and candidate creation.
     * Defaults: to = today, from = today - 12 months (if either is null).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BootstrapResultResponse trigger(
            @RequestBody(required = false) BootstrapTriggerRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        UUID actingUserId = JwtUtil.requireUuidClaim(jwt, "user_id");

        LocalDate to = (request != null && request.to() != null) ? request.to() : LocalDate.now();
        LocalDate from = (request != null && request.from() != null) ? request.from() : to.minusMonths(12);

        BootstrapResult result = bootstrapService.triggerBootstrap(tenantId, actingUserId, from, to);
        return BootstrapResultResponse.from(result);
    }

    /**
     * List bootstrap candidates with optional status filter and pagination.
     */
    @GetMapping("/candidates")
    public BootstrapCandidatesPageResponse listCandidates(
            @RequestParam(required = false) BootstrapCandidateStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        BootstrapTriageFilter filter = new BootstrapTriageFilter(status);
        BootstrapCandidatesPage result = bootstrapService.listCandidates(
                tenantId, filter, clampedPage, clampedSize);

        return BootstrapCandidatesPageResponse.from(result);
    }

    /**
     * Approve a candidate and promote it to a registry product.
     */
    @PostMapping("/candidates/{id}/approve")
    public BootstrapCandidateResponse approve(
            @PathVariable UUID id,
            @Valid @RequestBody BootstrapApproveRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        UUID actingUserId = JwtUtil.requireUuidClaim(jwt, "user_id");

        BootstrapCandidate candidate = bootstrapService.approveCandidateAndCreateProduct(
                tenantId, id, actingUserId, request.toCommand());

        return BootstrapCandidateResponse.from(candidate);
    }

    /**
     * Reject a candidate — sets status to REJECTED_NOT_OWN_PACKAGING or NEEDS_MANUAL_ENTRY.
     */
    @PostMapping("/candidates/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(
            @PathVariable UUID id,
            @Valid @RequestBody BootstrapRejectRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        UUID actingUserId = JwtUtil.requireUuidClaim(jwt, "user_id");

        BootstrapCandidateStatus targetStatus = mapRejectionReason(request.rejectionReason());
        bootstrapService.rejectCandidate(tenantId, id, actingUserId, targetStatus);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private BootstrapCandidateStatus mapRejectionReason(String reason) {
        if ("NOT_OWN_PACKAGING".equals(reason)) {
            return BootstrapCandidateStatus.REJECTED_NOT_OWN_PACKAGING;
        } else if ("NEEDS_MANUAL".equals(reason)) {
            return BootstrapCandidateStatus.NEEDS_MANUAL_ENTRY;
        }
        throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "rejectionReason must be NOT_OWN_PACKAGING or NEEDS_MANUAL");
    }
}
