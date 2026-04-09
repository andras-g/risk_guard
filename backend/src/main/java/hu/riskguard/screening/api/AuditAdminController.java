package hu.riskguard.screening.api;

import hu.riskguard.screening.api.dto.AdminAuditPageResponse;
import hu.riskguard.screening.domain.ScreeningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Admin REST controller for GDPR audit log search (Story 6.4).
 * Exposes cross-tenant audit search restricted to {@code PLATFORM_ADMIN} role only.
 */
@RestController
@RequestMapping("/api/v1/admin/screening")
@RequiredArgsConstructor
public class AuditAdminController {

    private final ScreeningService screeningService;

    /**
     * Search the audit log across all tenants. At least one search criterion is required.
     *
     * @param taxNumber filter by exact tax number (optional)
     * @param tenantId  filter by tenant ID (optional)
     * @param page      zero-based page index (default 0)
     * @param size      page size (default 20, clamped to [1, 100])
     * @param jwt       authenticated user's JWT — must have {@code role=PLATFORM_ADMIN}
     * @return paginated audit entries
     */
    @GetMapping("/audit")
    public AdminAuditPageResponse getAuditLog(
            @RequestParam(required = false) String taxNumber,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requirePlatformAdminRole(jwt);
        if (taxNumber == null && tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one of taxNumber or tenantId must be provided");
        }
        int clampedSize = Math.min(Math.max(size, 1), 100);
        int clampedPage = Math.max(page, 0);
        return AdminAuditPageResponse.from(
                screeningService.getAdminAuditLog(taxNumber, tenantId, clampedPage, clampedSize));
    }

    private void requirePlatformAdminRole(Jwt jwt) {
        if (!"PLATFORM_ADMIN".equals(jwt.getClaimAsString("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform admin access required");
        }
    }
}
