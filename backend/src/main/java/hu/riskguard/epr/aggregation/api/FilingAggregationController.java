package hu.riskguard.epr.aggregation.api;

import hu.riskguard.core.security.Tier;
import hu.riskguard.core.security.TierRequired;
import hu.riskguard.core.util.JwtUtil;
import hu.riskguard.epr.aggregation.api.dto.FilingAggregationResult;
import hu.riskguard.epr.aggregation.domain.InvoiceDrivenFilingAggregator;
import hu.riskguard.epr.producer.domain.ProducerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST controller for invoice-driven EPR filing aggregation (Story 10.5, AC #9).
 *
 * <p>Requires PRO_EPR tier and SME_ADMIN/ACCOUNTANT/PLATFORM_ADMIN role.
 * Returns 412 if the producer profile is incomplete.
 */
@RestController
@RequestMapping("/api/v1/epr")
@TierRequired(Tier.PRO_EPR)
@RequiredArgsConstructor
public class FilingAggregationController {

    private final InvoiceDrivenFilingAggregator aggregator;
    private final ProducerProfileService producerProfileService;

    @Operation(summary = "Compute EPR filing aggregation from invoice data for a period")
    @GetMapping("/filing/aggregation")
    public ResponseEntity<FilingAggregationResult> aggregate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse response) {

        JwtUtil.requireRole(jwt, "Filing aggregation requires SME_ADMIN, ACCOUNTANT, or PLATFORM_ADMIN role",
                "SME_ADMIN", "ACCOUNTANT", "PLATFORM_ADMIN");
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");

        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reporting period start must not be after end");
        }

        // 412 if producer profile is incomplete (throws ResponseStatusException)
        producerProfileService.get(tenantId);

        response.setHeader("Cache-Control", "max-age=60, private");
        FilingAggregationResult result = aggregator.aggregateForPeriod(tenantId, from, to);
        return ResponseEntity.ok(result);
    }
}
