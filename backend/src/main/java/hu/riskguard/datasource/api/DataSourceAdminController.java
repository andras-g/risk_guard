package hu.riskguard.datasource.api;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.util.JwtUtil;
import hu.riskguard.datasource.api.dto.AdapterHealthResponse;
import hu.riskguard.datasource.api.dto.QuarantineRequest;
import hu.riskguard.datasource.domain.CompanyDataPort;
import hu.riskguard.datasource.internal.AdapterHealthRepository;
import hu.riskguard.datasource.internal.AdapterHealthRepository.AdapterHealthRow;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin REST controller for data source adapter health monitoring.
 * Restricted to {@code SME_ADMIN} role only.
 *
 * <p>Merges live Resilience4j circuit breaker metrics with persisted {@code adapter_health}
 * DB rows to produce a unified health snapshot per adapter.
 */
@RestController
@RequestMapping("/api/v1/admin/datasources")
@RequiredArgsConstructor
public class DataSourceAdminController {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final AdapterHealthRepository adapterHealthRepository;
    private final List<CompanyDataPort> adapters;
    private final RiskGuardProperties riskGuardProperties;

    /**
     * Returns health status for all registered data source adapters.
     * Requires {@code SME_ADMIN} role; returns 403 for all other roles.
     */
    @GetMapping("/health")
    public List<AdapterHealthResponse> getHealth(@AuthenticationPrincipal Jwt jwt) {
        requireAdminRole(jwt);

        String dataSourceMode = riskGuardProperties.getDataSource().getMode().toUpperCase();
        List<AdapterHealthRow> dbRows = adapterHealthRepository.findAll();

        List<String> adapterNames = adapters.stream().map(CompanyDataPort::adapterName).toList();
        Map<String, String> credentialStatuses = adapterHealthRepository.findAllCredentialStatuses(adapterNames);

        return adapters.stream()
                .map(adapter -> buildResponse(adapter, dataSourceMode, dbRows, credentialStatuses))
                .toList();
    }

    /**
     * Manually quarantines or releases an adapter by transitioning its circuit breaker
     * to {@code FORCED_OPEN} (quarantine) or {@code CLOSED} (release).
     * Restricted to {@code SME_ADMIN} role; returns 404 for unknown adapters,
     * 422 if no circuit breaker is registered for the adapter.
     */
    @PostMapping("/{adapterName}/quarantine")
    public AdapterHealthResponse quarantine(
            @PathVariable String adapterName,
            @RequestBody QuarantineRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireAdminRole(jwt);

        CompanyDataPort adapter = adapters.stream()
                .filter(a -> a.adapterName().equals(adapterName))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Adapter not found: " + adapterName));

        CircuitBreaker cb = circuitBreakerRegistry.find(adapterName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No circuit breaker registered for adapter '" + adapterName + "'"));

        // P3: Releasing a non-quarantined CB causes IllegalStateException in Resilience4j
        if (!request.quarantined() && cb.getState() != CircuitBreaker.State.FORCED_OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Adapter is not quarantined");
        }

        // P1+P2: DB writes first (atomic), CB transition after — if DB fails, CB is unchanged;
        // if CB transition fails (in-memory, unlikely), DB is committed but init() self-heals on restart.
        Instant now = Instant.now();
        UUID actorUserId = JwtUtil.requireUuidClaim(jwt, "user_id");
        String actionName = request.quarantined() ? "QUARANTINE" : "RELEASE_QUARANTINE";
        String details = "{\"quarantined\": " + request.quarantined() + "}";
        adapterHealthRepository.setQuarantinedAndLogAction(adapterName, request.quarantined(), actorUserId, actionName, details, now);

        if (request.quarantined()) {
            cb.transitionToForcedOpenState();
        } else {
            cb.transitionToClosedState();
        }

        String dataSourceMode = riskGuardProperties.getDataSource().getMode().toUpperCase();
        List<AdapterHealthRow> dbRows = adapterHealthRepository.findAll();
        List<String> adapterNames = adapters.stream().map(CompanyDataPort::adapterName).toList();
        Map<String, String> credentialStatuses = adapterHealthRepository.findAllCredentialStatuses(adapterNames);

        return buildResponse(adapter, dataSourceMode, dbRows, credentialStatuses);
    }

    private AdapterHealthResponse buildResponse(
            CompanyDataPort adapter,
            String dataSourceMode,
            List<AdapterHealthRow> dbRows,
            Map<String, String> credentialStatuses
    ) {
        String name = adapter.adapterName();
        Optional<CircuitBreaker> cbOpt = circuitBreakerRegistry.find(name);

        String cbState;
        double successRatePct;

        if (cbOpt.isPresent()) {
            CircuitBreaker cb = cbOpt.get();
            cbState = cb.getState().name();
            float failureRate = cb.getMetrics().getFailureRate();
            // failureRate is -1 if not enough calls; treat as 0 failures (100% success)
            successRatePct = failureRate < 0 ? 100.0 : Math.max(0.0, 100.0 - failureRate);
        } else {
            // No circuit breaker registered — adapter considered always available (e.g., pure demo)
            cbState = "DISABLED";
            successRatePct = 100.0;
        }

        // AC#2: Demo mode always reports healthy — but NOT when manually quarantined (P8: FORCED_OPEN
        // is an explicit admin action that must remain visible even in demo mode).
        if ("DEMO".equals(dataSourceMode) && !"FORCED_OPEN".equals(cbState)) {
            cbState = "CLOSED";
            successRatePct = 100.0;
        }

        AdapterHealthRow dbRow = dbRows.stream()
                .filter(r -> name.equals(r.adapterName()))
                .findFirst()
                .orElse(null);

        int failureCount = dbRow != null ? dbRow.failureCount() : 0;
        Instant lastSuccessAt = dbRow != null ? dbRow.lastSuccessAt() : null;
        Instant lastFailureAt = dbRow != null ? dbRow.lastFailureAt() : null;
        Double mtbfHours = dbRow != null ? dbRow.mtbfHours() : null;

        // AC#2: Demo mode credential status is always NOT_CONFIGURED (no real credentials)
        String credentialStatus = "DEMO".equals(dataSourceMode)
                ? "NOT_CONFIGURED"
                : credentialStatuses.getOrDefault(name, "NOT_CONFIGURED");

        return AdapterHealthResponse.from(
                name,
                cbState,
                successRatePct,
                failureCount,
                lastSuccessAt,
                lastFailureAt,
                mtbfHours,
                dataSourceMode,
                credentialStatus
        );
    }

    private void requireAdminRole(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        if (!"SME_ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }
}
