package hu.riskguard.epr.registry.api;

import hu.riskguard.core.security.Tier;
import hu.riskguard.core.security.TierRequired;
import hu.riskguard.core.util.JwtUtil;
import hu.riskguard.epr.registry.api.dto.ClassifyRequest;
import hu.riskguard.epr.registry.api.dto.ClassifyResponse;
import hu.riskguard.epr.registry.classifier.ClassificationResult;
import hu.riskguard.epr.registry.classifier.KfCodeClassifierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for AI-assisted KF-code classification.
 * Requires {@code PRO_EPR} tier. Tenant identity from JWT.
 *
 * <p>{@code POST /api/v1/registry/classify} — calls {@link KfCodeClassifierService#classify}
 * (routed to Gemini → VTSZ-prefix fallback by {@code ClassifierRouter}).
 */
@RestController
@RequestMapping("/api/v1/registry/classify")
@RequiredArgsConstructor
@TierRequired(Tier.PRO_EPR)
public class RegistryClassifyController {

    private final KfCodeClassifierService classifierService;

    @PostMapping
    public ClassifyResponse classify(
            @Valid @RequestBody ClassifyRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        // tenantId used by TenantContext (set by TenantFilter) — not passed to classifier directly
        JwtUtil.requireUuidClaim(jwt, "active_tenant_id");

        ClassificationResult result = classifierService.classify(request.productName(), request.vtsz());
        return ClassifyResponse.from(result);
    }
}
