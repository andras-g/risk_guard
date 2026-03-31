package hu.riskguard.datasource;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.datasource.api.DataSourceAdminController;
import hu.riskguard.datasource.api.dto.AdapterHealthResponse;
import hu.riskguard.datasource.api.dto.QuarantineRequest;
import hu.riskguard.datasource.domain.CompanyDataPort;
import hu.riskguard.datasource.internal.AdapterHealthRepository;
import hu.riskguard.datasource.internal.AdapterHealthRepository.AdapterHealthRow;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DataSourceAdminController}.
 * Pure Mockito — no Spring context. Follows PortfolioControllerTest pattern.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataSourceAdminControllerTest {

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private AdapterHealthRepository adapterHealthRepository;

    @Mock
    private RiskGuardProperties riskGuardProperties;

    @Mock
    private RiskGuardProperties.DataSource dataSourceProps;

    private DataSourceAdminController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();

    private final CompanyDataPort demoAdapter = new CompanyDataPort() {
        @Override
        public hu.riskguard.datasource.api.dto.ScrapedData fetch(String taxNumber) {
            return null;
        }

        @Override
        public String adapterName() {
            return "demo";
        }

        @Override
        public Set<String> requiredFields() {
            return Set.of();
        }
    };

    @BeforeEach
    void setUp() {
        controller = new DataSourceAdminController(
                circuitBreakerRegistry,
                adapterHealthRepository,
                List.of(demoAdapter),
                riskGuardProperties
        );
        when(riskGuardProperties.getDataSource()).thenReturn(dataSourceProps);
        when(dataSourceProps.getMode()).thenReturn("demo");
        when(adapterHealthRepository.findAll()).thenReturn(List.of());
        when(adapterHealthRepository.findAllCredentialStatuses(anyList()))
                .thenReturn(Map.of("demo", "NOT_CONFIGURED"));
    }

    @Test
    void smeAdminGetsHealthList() {
        Jwt jwt = buildJwtWithRole("SME_ADMIN");
        when(circuitBreakerRegistry.find("demo")).thenReturn(Optional.empty());

        List<AdapterHealthResponse> result = controller.getHealth(jwt);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).adapterName()).isEqualTo("demo");
        assertThat(result.get(0).dataSourceMode()).isEqualTo("DEMO");
        assertThat(result.get(0).credentialStatus()).isEqualTo("NOT_CONFIGURED");
    }

    @Test
    void nonAdminGets403() {
        Jwt jwt = buildJwtWithRole("ACCOUNTANT");

        assertThatThrownBy(() -> controller.getHealth(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void guestGets403() {
        Jwt jwt = buildJwtWithRole("GUEST");

        assertThatThrownBy(() -> controller.getHealth(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void noCbRegistryReturnsDisabledState() {
        // Use non-DEMO mode so the DEMO override does not mask the DISABLED path
        Jwt jwt = buildJwtWithRole("SME_ADMIN");
        when(dataSourceProps.getMode()).thenReturn("live");
        when(circuitBreakerRegistry.find("demo")).thenReturn(Optional.empty());

        List<AdapterHealthResponse> result = controller.getHealth(jwt);

        assertThat(result.get(0).circuitBreakerState()).isEqualTo("DISABLED");
        assertThat(result.get(0).successRatePct()).isEqualTo(100.0);
    }

    @Test
    void cbRegistryClosedStateReflected() {
        Jwt jwt = buildJwtWithRole("SME_ADMIN");
        CircuitBreaker cb = CircuitBreaker.ofDefaults("demo");
        when(circuitBreakerRegistry.find("demo")).thenReturn(Optional.of(cb));

        List<AdapterHealthResponse> result = controller.getHealth(jwt);

        assertThat(result.get(0).circuitBreakerState()).isEqualTo("CLOSED");
    }

    @Test
    void dbRowMergedIntoResponse() {
        Jwt jwt = buildJwtWithRole("SME_ADMIN");
        when(circuitBreakerRegistry.find("demo")).thenReturn(Optional.empty());

        Instant lastSuccess = Instant.parse("2026-03-31T10:00:00Z");
        Instant lastFailure = Instant.parse("2026-03-31T09:00:00Z");
        AdapterHealthRow row = new AdapterHealthRow("demo", "HEALTHY", lastSuccess, lastFailure, 3, 2.5);
        when(adapterHealthRepository.findAll()).thenReturn(List.of(row));

        List<AdapterHealthResponse> result = controller.getHealth(jwt);

        assertThat(result.get(0).failureCount()).isEqualTo(3);
        assertThat(result.get(0).lastSuccessAt()).isEqualTo(lastSuccess);
        assertThat(result.get(0).lastFailureAt()).isEqualTo(lastFailure);
        assertThat(result.get(0).mtbfHours()).isEqualTo(2.5);
    }

    @Test
    void demoModeUppercasedInResponse() {
        Jwt jwt = buildJwtWithRole("SME_ADMIN");
        when(dataSourceProps.getMode()).thenReturn("demo");
        when(circuitBreakerRegistry.find("demo")).thenReturn(Optional.empty());

        List<AdapterHealthResponse> result = controller.getHealth(jwt);

        assertThat(result.get(0).dataSourceMode()).isEqualTo("DEMO");
    }

    @Test
    void demoModeOverridesCredentialStatusToNotConfigured() {
        // AC#2: credentialStatus must always be NOT_CONFIGURED in DEMO mode,
        // even if nav_credentials contains a different status for this adapter
        Jwt jwt = buildJwtWithRole("SME_ADMIN");
        when(circuitBreakerRegistry.find("demo")).thenReturn(Optional.empty());
        when(dataSourceProps.getMode()).thenReturn("demo");
        when(adapterHealthRepository.findAllCredentialStatuses(anyList()))
                .thenReturn(Map.of("demo", "VALID"));

        List<AdapterHealthResponse> result = controller.getHealth(jwt);

        assertThat(result.get(0).credentialStatus()).isEqualTo("NOT_CONFIGURED");
    }

    @Test
    void demoModeOverridesOpenCircuitBreakerToClosed() {
        // AC#2: Demo mode must always show CLOSED/100% regardless of live CB state
        Jwt jwt = buildJwtWithRole("SME_ADMIN");
        CircuitBreaker cb = mock(CircuitBreaker.class);
        CircuitBreaker.Metrics metrics = mock(CircuitBreaker.Metrics.class);
        when(cb.getState()).thenReturn(CircuitBreaker.State.OPEN);
        when(cb.getMetrics()).thenReturn(metrics);
        when(metrics.getFailureRate()).thenReturn(100f);
        when(circuitBreakerRegistry.find("demo")).thenReturn(Optional.of(cb));
        when(dataSourceProps.getMode()).thenReturn("demo");

        List<AdapterHealthResponse> result = controller.getHealth(jwt);

        assertThat(result.get(0).circuitBreakerState()).isEqualTo("CLOSED");
        assertThat(result.get(0).successRatePct()).isEqualTo(100.0);
    }

    // --- Quarantine endpoint tests ---

    @Test
    void quarantineAdapter_smeAdmin_returns200AndForcedOpen() {
        Jwt jwt = buildJwtWithRoleAndUserId("SME_ADMIN");
        CircuitBreaker mockCb = mock(CircuitBreaker.class);
        CircuitBreaker.Metrics metrics = mock(CircuitBreaker.Metrics.class);
        when(mockCb.getState()).thenReturn(CircuitBreaker.State.FORCED_OPEN);
        when(mockCb.getMetrics()).thenReturn(metrics);
        when(metrics.getFailureRate()).thenReturn(-1f);
        when(circuitBreakerRegistry.find("demo")).thenReturn(Optional.of(mockCb));
        when(dataSourceProps.getMode()).thenReturn("live");

        AdapterHealthResponse result = controller.quarantine("demo", new QuarantineRequest(true), jwt);

        verify(mockCb).transitionToForcedOpenState();
        verify(adapterHealthRepository).setQuarantinedAndLogAction(
                eq("demo"), eq(true), any(UUID.class), eq("QUARANTINE"), anyString(), any(Instant.class));
        assertThat(result.adapterName()).isEqualTo("demo");
        assertThat(result.circuitBreakerState()).isEqualTo("FORCED_OPEN");
    }

    @Test
    void releaseQuarantine_smeAdmin_returns200AndClosed() {
        Jwt jwt = buildJwtWithRoleAndUserId("SME_ADMIN");
        CircuitBreaker mockCb = mock(CircuitBreaker.class);
        CircuitBreaker.Metrics metrics = mock(CircuitBreaker.Metrics.class);
        // P3 guard checks state first (must be FORCED_OPEN); buildResponse sees CLOSED after transition
        when(mockCb.getState()).thenReturn(CircuitBreaker.State.FORCED_OPEN, CircuitBreaker.State.CLOSED);
        when(mockCb.getMetrics()).thenReturn(metrics);
        when(metrics.getFailureRate()).thenReturn(-1f);
        when(circuitBreakerRegistry.find("demo")).thenReturn(Optional.of(mockCb));
        when(dataSourceProps.getMode()).thenReturn("live");

        AdapterHealthResponse result = controller.quarantine("demo", new QuarantineRequest(false), jwt);

        verify(mockCb).transitionToClosedState();
        verify(adapterHealthRepository).setQuarantinedAndLogAction(
                eq("demo"), eq(false), any(UUID.class), eq("RELEASE_QUARANTINE"), anyString(), any(Instant.class));
        assertThat(result.circuitBreakerState()).isEqualTo("CLOSED");
    }

    @Test
    void releaseQuarantine_notQuarantined_returns409() {
        // P3: CB not in FORCED_OPEN → 409 Conflict; transitionToClosedState must NOT be called
        Jwt jwt = buildJwtWithRoleAndUserId("SME_ADMIN");
        CircuitBreaker mockCb = mock(CircuitBreaker.class);
        when(mockCb.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(circuitBreakerRegistry.find("demo")).thenReturn(Optional.of(mockCb));
        when(dataSourceProps.getMode()).thenReturn("live");

        assertThatThrownBy(() -> controller.quarantine("demo", new QuarantineRequest(false), jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(mockCb, never()).transitionToClosedState();
        verify(adapterHealthRepository, never()).setQuarantinedAndLogAction(
                anyString(), any(Boolean.class), any(), anyString(), anyString(), any());
    }

    @Test
    void quarantineAdapter_nonAdmin_returns403() {
        Jwt jwt = buildJwtWithRole("ACCOUNTANT");

        assertThatThrownBy(() -> controller.quarantine("demo", new QuarantineRequest(true), jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verify(adapterHealthRepository, never()).setQuarantinedAndLogAction(
                anyString(), any(Boolean.class), any(), anyString(), anyString(), any());
    }

    @Test
    void quarantineAdapter_unknownAdapter_returns404() {
        Jwt jwt = buildJwtWithRoleAndUserId("SME_ADMIN");

        assertThatThrownBy(() -> controller.quarantine("unknown-adapter", new QuarantineRequest(true), jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void quarantineAdapter_noCircuitBreaker_returns422() {
        Jwt jwt = buildJwtWithRoleAndUserId("SME_ADMIN");
        when(circuitBreakerRegistry.find("demo")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.quarantine("demo", new QuarantineRequest(true), jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    private Jwt buildJwtWithRole(String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user@test.com")
                .claim("role", role)
                .claim("active_tenant_id", TENANT_ID.toString())
                .build();
    }

    private Jwt buildJwtWithRoleAndUserId(String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user@test.com")
                .claim("role", role)
                .claim("active_tenant_id", TENANT_ID.toString())
                .claim("user_id", UUID.randomUUID().toString())
                .build();
    }
}
