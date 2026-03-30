package hu.riskguard.screening.api;

import hu.riskguard.core.util.JwtUtil;
import hu.riskguard.screening.api.dto.AuditHashVerifyResponse;
import hu.riskguard.screening.api.dto.AuditHistoryPageResponse;
import hu.riskguard.screening.domain.AuditHistoryFilter;
import hu.riskguard.screening.domain.ScreeningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller for the audit history feature (Story 5.1a).
 * Exposes tenant-scoped, paginated audit trail and hash-verify endpoints.
 *
 * <p>PII safety (@LogSafe): tax numbers in request params are NOT logged.
 * Only UUIDs (auditId, tenantId) appear in log statements.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/screening/audit-history")
@RequiredArgsConstructor
public class AuditHistoryController {

    private final ScreeningService screeningService;

    /**
     * Get a paginated, filtered list of audit history entries for the authenticated tenant.
     *
     * @param page        zero-based page index (default 0)
     * @param size        page size (default 20, max 100)
     * @param sortDir     "DESC" or "ASC" — currently only DESC is supported (default)
     * @param startDate   filter: inclusive lower bound on searched_at (ISO date, e.g. 2026-01-01)
     * @param endDate     filter: inclusive upper bound on searched_at (ISO date, e.g. 2026-12-31)
     * @param taxNumber   filter: exact tax number match
     * @param checkSource filter: "MANUAL" or "AUTOMATED" (null = all)
     * @param jwt         authenticated user's JWT (tenant resolved via TenantFilter)
     * @return paginated audit history
     */
    @GetMapping
    public AuditHistoryPageResponse getAuditHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String taxNumber,
            @RequestParam(required = false) String checkSource,
            @AuthenticationPrincipal Jwt jwt) {

        JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        validateCheckSource(checkSource);

        int clampedSize = Math.min(Math.max(size, 1), 100);
        int clampedPage = Math.max(page, 0);

        AuditHistoryFilter filter = new AuditHistoryFilter(startDate, endDate, taxNumber, checkSource);
        return AuditHistoryPageResponse.from(
                screeningService.getAuditHistory(filter, clampedPage, clampedSize));
    }

    /**
     * Re-compute the SHA-256 hash for an audit entry and compare with the stored value.
     *
     * @param id  the audit log entry UUID
     * @param jwt authenticated user's JWT
     * @return match result with computed and stored hash values
     */
    @GetMapping("/{id}/verify-hash")
    public AuditHashVerifyResponse verifyHash(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        JwtUtil.requireUuidClaim(jwt, "active_tenant_id");

        return screeningService.verifyAuditHash(id)
                .map(AuditHashVerifyResponse::from)
                .orElseThrow(() -> problemDetail(HttpStatus.NOT_FOUND,
                        "Not Found", "Audit entry not found or not accessible"));
    }

    private static final Set<String> VALID_CHECK_SOURCES = Set.of("MANUAL", "AUTOMATED");

    private void validateCheckSource(String checkSource) {
        if (checkSource != null && !VALID_CHECK_SOURCES.contains(checkSource)) {
            throw problemDetail(HttpStatus.BAD_REQUEST,
                    "Bad Request", "checkSource must be MANUAL or AUTOMATED");
        }
    }

    private static ErrorResponseException problemDetail(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return new ErrorResponseException(status, problem, null);
    }
}
