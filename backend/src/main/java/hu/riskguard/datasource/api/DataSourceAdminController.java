package hu.riskguard.datasource.api;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.datasource.api.dto.AdapterHealthResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

        // AC#2: Demo mode always reports healthy regardless of live circuit breaker state
        if ("DEMO".equals(dataSourceMode)) {
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
