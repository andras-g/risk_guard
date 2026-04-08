package hu.riskguard.datasource.api;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.util.JwtUtil;
import hu.riskguard.datasource.api.dto.AdapterHealthResponse;
import hu.riskguard.datasource.api.dto.NavCredentialRequest;
import hu.riskguard.datasource.api.dto.QuarantineRequest;
import hu.riskguard.datasource.domain.CompanyDataPort;
import hu.riskguard.datasource.internal.AdapterHealthRepository;
import hu.riskguard.datasource.internal.AdapterHealthRepository.AdapterHealthRow;
import hu.riskguard.datasource.internal.AesFieldEncryptor;
import hu.riskguard.datasource.internal.NavTenantCredentialRepository;
import hu.riskguard.datasource.internal.adapters.nav.AuthService;
import hu.riskguard.datasource.internal.adapters.nav.NavCredentials;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
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
public class DataSourceAdminController {

    private static final String NAV_ADAPTER_NAME = "nav-online-szamla";

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final AdapterHealthRepository adapterHealthRepository;
    private final List<CompanyDataPort> adapters;
    private final RiskGuardProperties riskGuardProperties;
    private final AuthService authService;
    private final AesFieldEncryptor aesFieldEncryptor;
    private final NavTenantCredentialRepository navTenantCredentialRepository;

    public DataSourceAdminController(
            CircuitBreakerRegistry circuitBreakerRegistry,
            AdapterHealthRepository adapterHealthRepository,
            List<CompanyDataPort> adapters,
            RiskGuardProperties riskGuardProperties,
            AuthService authService,
            AesFieldEncryptor aesFieldEncryptor,
            NavTenantCredentialRepository navTenantCredentialRepository
    ) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.adapterHealthRepository = adapterHealthRepository;
        this.adapters = adapters;
        this.riskGuardProperties = riskGuardProperties;
        this.authService = authService;
        this.aesFieldEncryptor = aesFieldEncryptor;
        this.navTenantCredentialRepository = navTenantCredentialRepository;
    }

    /**
     * Returns health status for all registered data source adapters.
     * Requires {@code SME_ADMIN} role; returns 403 for all other roles.
     */
    @GetMapping("/health")
    public List<AdapterHealthResponse> getHealth(@AuthenticationPrincipal Jwt jwt) {
        requireAdminOrAccountantRole(jwt);
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");

        String dataSourceMode = riskGuardProperties.getDataSource().getMode().toUpperCase();
        List<AdapterHealthRow> dbRows = adapterHealthRepository.findAll();

        // Derive NAV credential status from per-tenant nav_tenant_credentials table
        String navCredentialStatus = navTenantCredentialRepository.existsByTenantId(tenantId)
                ? "VALID" : "NOT_CONFIGURED";

        return adapters.stream()
                .map(adapter -> buildResponse(adapter, dataSourceMode, dbRows, navCredentialStatus))
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
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");
        String navCredentialStatus = navTenantCredentialRepository.existsByTenantId(tenantId)
                ? "VALID" : "NOT_CONFIGURED";

        return buildResponse(adapter, dataSourceMode, dbRows, navCredentialStatus);
    }

    /**
     * Saves or updates NAV Online Számla credentials for the currently active tenant.
     * Verifies the credentials via the NAV {@code /tokenExchange} endpoint before persisting.
     * Returns {@code 422 Unprocessable Entity} if verification fails.
     */
    @PutMapping("/credentials")
    public void saveCredentials(
            @RequestBody @Valid NavCredentialRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireAdminOrAccountantRole(jwt);
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");

        String passwordHash = authService.hashPassword(request.password());
        String loginEnc = aesFieldEncryptor.encrypt(request.login());
        String signingKeyEnc = aesFieldEncryptor.encrypt(request.signingKey());
        String exchangeKeyEnc = aesFieldEncryptor.encrypt(request.exchangeKey());

        // Demo mode: skip NAV verification (no live NAV connection available)
        boolean isDemoMode = "demo".equalsIgnoreCase(riskGuardProperties.getDataSource().getMode());
        if (!isDemoMode) {
            NavCredentials credentials = new NavCredentials(
                    request.login(), passwordHash, request.signingKey(),
                    request.exchangeKey(), request.taxNumber()
            );
            boolean valid = authService.verifyCredentials(credentials);
            if (!valid) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "NAV credential verification failed — check login, password, signingKey, and exchangeKey");
            }
        }

        navTenantCredentialRepository.upsert(tenantId, loginEnc, passwordHash,
                signingKeyEnc, exchangeKeyEnc, request.taxNumber());
    }

    /**
     * Removes NAV Online Számla credentials for the currently active tenant.
     * Resets the credential status to {@code NOT_CONFIGURED}.
     */
    @DeleteMapping("/credentials")
    public void deleteCredentials(@AuthenticationPrincipal Jwt jwt) {
        requireAdminOrAccountantRole(jwt);
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");

        navTenantCredentialRepository.deleteByTenantId(tenantId);
    }

    private AdapterHealthResponse buildResponse(
            CompanyDataPort adapter,
            String dataSourceMode,
            List<AdapterHealthRow> dbRows,
            String navCredentialStatus
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

        String credentialStatus = (NAV_ADAPTER_NAME.equals(name) || "DEMO".equals(dataSourceMode))
                ? navCredentialStatus : "NOT_CONFIGURED";

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

    private void requireAdminOrAccountantRole(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        if (!"SME_ADMIN".equals(role) && !"ACCOUNTANT".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin or Accountant access required");
        }
    }
}
