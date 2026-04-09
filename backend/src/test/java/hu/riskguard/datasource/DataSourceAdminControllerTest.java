package hu.riskguard.datasource;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.datasource.api.DataSourceAdminController;
import hu.riskguard.datasource.api.dto.AdapterHealthResponse;
import hu.riskguard.datasource.api.dto.QuarantineRequest;
import hu.riskguard.datasource.domain.CompanyDataPort;
import hu.riskguard.datasource.internal.AdapterHealthRepository;
import hu.riskguard.datasource.internal.AdapterHealthRepository.AdapterHealthRow;
import hu.riskguard.datasource.internal.AesFieldEncryptor;
import hu.riskguard.datasource.internal.NavTenantCredentialRepository;
import hu.riskguard.datasource.internal.adapters.nav.AuthService;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private AuthService authService;

    @Mock
    private AesFieldEncryptor aesFieldEncryptor;

    @Mock
    private NavTenantCredentialRepository navTenantCredentialRepository;

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
                riskGuardProperties,
                authService,
                aesFieldEncryptor,
                navTenantCredentialRepository
        );
        when(riskGuardProperties.getDataSource()).thenReturn(dataSourceProps);
        when(dataSourceProps.getMode()).thenReturn("demo");
        when(adapterHealthRepository.findAll()).thenReturn(List.of());
        when(navTenantCredentialRepository.existsByTenantId(any())).thenReturn(false);
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
        Jwt jwt = buildJwtWithRole("GUEST");

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
    void demoModeDerivesCredentialStatusFromDb() {
        // AC#3: Demo mode credential status derived from DB — after saving credentials in demo mode,
        // badge shows VALID (adapter name "demo" treated same as "nav-online-szamla" for credential status)
        Jwt jwt = buildJwtWithRole("SME_ADMIN");
        when(circuitBreakerRegistry.find("demo")).thenReturn(Optional.empty());
        when(dataSourceProps.getMode()).thenReturn("demo");
        when(navTenantCredentialRepository.existsByTenantId(any())).thenReturn(true);

        List<AdapterHealthResponse> result = controller.getHealth(jwt);

        assertThat(result.get(0).credentialStatus()).isEqualTo("VALID");
    }

    @Test
    void demoModeCredentialStatusNotConfiguredWhenNoCreds() {
        // AC#3: In demo mode, no credentials stored → NOT_CONFIGURED
        Jwt jwt = buildJwtWithRole("SME_ADMIN");
        when(circuitBreakerRegistry.find("demo")).thenReturn(Optional.empty());
        when(dataSourceProps.getMode()).thenReturn("demo");
        when(navTenantCredentialRepository.existsByTenantId(any())).thenReturn(false);

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
    void quarantineAdapter_platformAdmin_returns200AndForcedOpen() {
        Jwt jwt = buildJwtWithRoleAndUserId("PLATFORM_ADMIN");
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
    void releaseQuarantine_platformAdmin_returns200AndClosed() {
        Jwt jwt = buildJwtWithRoleAndUserId("PLATFORM_ADMIN");
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
        Jwt jwt = buildJwtWithRoleAndUserId("PLATFORM_ADMIN");
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
    void quarantineAdapter_smeAdmin_returns403() {
        Jwt jwt = buildJwtWithRole("SME_ADMIN");

        assertThatThrownBy(() -> controller.quarantine("demo", new QuarantineRequest(true), jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
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
        Jwt jwt = buildJwtWithRoleAndUserId("PLATFORM_ADMIN");

        assertThatThrownBy(() -> controller.quarantine("unknown-adapter", new QuarantineRequest(true), jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void quarantineAdapter_noCircuitBreaker_returns422() {
        Jwt jwt = buildJwtWithRoleAndUserId("PLATFORM_ADMIN");
        when(circuitBreakerRegistry.find("demo")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.quarantine("demo", new QuarantineRequest(true), jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // --- Credential endpoint tests ---

    @Test
    void saveCredentials_smeAdmin_validCredentials_returns200() {
        Jwt jwt = buildJwtWithRoleAndUserId("SME_ADMIN");
        when(dataSourceProps.getMode()).thenReturn("test");
        var request = new hu.riskguard.datasource.api.dto.NavCredentialRequest(
                "testLogin", "rawPass", "signingKey", "exchangeKey", "12345678");
        when(authService.hashPassword("rawPass")).thenReturn("HASHXXX");
        when(aesFieldEncryptor.encrypt("testLogin")).thenReturn("enc_login");
        when(aesFieldEncryptor.encrypt("signingKey")).thenReturn("enc_signing");
        when(aesFieldEncryptor.encrypt("exchangeKey")).thenReturn("enc_exchange");
        when(authService.verifyCredentials(any())).thenReturn(true);

        controller.saveCredentials(request, jwt);

        verify(navTenantCredentialRepository).upsert(eq(TENANT_ID), anyString(), anyString(), anyString(), anyString(), eq("12345678"));
    }

    @Test
    void saveCredentials_invalidCredentials_returns422() {
        Jwt jwt = buildJwtWithRoleAndUserId("SME_ADMIN");
        when(dataSourceProps.getMode()).thenReturn("test");
        var request = new hu.riskguard.datasource.api.dto.NavCredentialRequest(
                "testLogin", "wrongPass", "signingKey", "exchangeKey", "12345678");
        when(authService.hashPassword(any())).thenReturn("HASH");
        when(aesFieldEncryptor.encrypt(any())).thenReturn("enc");
        when(authService.verifyCredentials(any())).thenReturn(false);

        assertThatThrownBy(() -> controller.saveCredentials(request, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(navTenantCredentialRepository, never()).upsert(any(), any(), any(), any(), any(), any());
    }

    @Test
    void saveCredentials_nonAdmin_returns403() {
        Jwt jwt = buildJwtWithRole("GUEST");
        var request = new hu.riskguard.datasource.api.dto.NavCredentialRequest(
                "testLogin", "rawPass", "signingKey", "exchangeKey", "12345678");

        assertThatThrownBy(() -> controller.saveCredentials(request, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void deleteCredentials_nonAdmin_returns403() {
        Jwt jwt = buildJwtWithRole("GUEST");

        assertThatThrownBy(() -> controller.deleteCredentials(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(navTenantCredentialRepository, never()).deleteByTenantId(any());
    }

    @Test
    void deleteCredentials_smeAdmin_returns200() {
        Jwt jwt = buildJwtWithRoleAndUserId("SME_ADMIN");

        controller.deleteCredentials(jwt);

        verify(navTenantCredentialRepository).deleteByTenantId(TENANT_ID);
    }

    // --- PLATFORM_ADMIN role tests ---

    @Test
    void getHealth_platformAdmin_returns200() {
        Jwt jwt = buildJwtWithRole("PLATFORM_ADMIN");
        when(circuitBreakerRegistry.find("demo")).thenReturn(Optional.empty());

        List<AdapterHealthResponse> result = controller.getHealth(jwt);

        assertThat(result).hasSize(1);
    }

    @Test
    void saveCredentials_platformAdmin_returns200() {
        Jwt jwt = buildJwtWithRoleAndUserId("PLATFORM_ADMIN");
        when(dataSourceProps.getMode()).thenReturn("test");
        var request = new hu.riskguard.datasource.api.dto.NavCredentialRequest(
                "testLogin", "rawPass", "signingKey", "exchangeKey", "12345678");
        when(authService.hashPassword("rawPass")).thenReturn("HASHXXX");
        when(aesFieldEncryptor.encrypt(anyString())).thenReturn("enc");
        when(authService.verifyCredentials(any())).thenReturn(true);

        controller.saveCredentials(request, jwt);

        verify(navTenantCredentialRepository).upsert(eq(TENANT_ID), anyString(), anyString(), anyString(), anyString(), eq("12345678"));
    }

    @Test
    void deleteCredentials_platformAdmin_returns200() {
        Jwt jwt = buildJwtWithRoleAndUserId("PLATFORM_ADMIN");

        controller.deleteCredentials(jwt);

        verify(navTenantCredentialRepository).deleteByTenantId(TENANT_ID);
    }

    // --- ACCOUNTANT role tests ---

    @Test
    void getHealth_accountant_returns200() {
        Jwt jwt = buildJwtWithRole("ACCOUNTANT");
        when(circuitBreakerRegistry.find("demo")).thenReturn(Optional.empty());

        List<AdapterHealthResponse> result = controller.getHealth(jwt);

        assertThat(result).hasSize(1);
    }

    @Test
    void saveCredentials_accountant_returns200() {
        Jwt jwt = buildJwtWithRoleAndUserId("ACCOUNTANT");
        when(dataSourceProps.getMode()).thenReturn("test");
        var request = new hu.riskguard.datasource.api.dto.NavCredentialRequest(
                "testLogin", "rawPass", "signingKey", "exchangeKey", "12345678");
        when(authService.hashPassword("rawPass")).thenReturn("HASHXXX");
        when(aesFieldEncryptor.encrypt(anyString())).thenReturn("enc");
        when(authService.verifyCredentials(any())).thenReturn(true);

        controller.saveCredentials(request, jwt);

        verify(navTenantCredentialRepository).upsert(eq(TENANT_ID), anyString(), anyString(), anyString(), anyString(), eq("12345678"));
    }

    @Test
    void deleteCredentials_accountant_returns200() {
        Jwt jwt = buildJwtWithRoleAndUserId("ACCOUNTANT");

        controller.deleteCredentials(jwt);

        verify(navTenantCredentialRepository).deleteByTenantId(TENANT_ID);
    }

    @Test
    void quarantine_accountant_returns403() {
        Jwt jwt = buildJwtWithRole("ACCOUNTANT");

        assertThatThrownBy(() -> controller.quarantine("demo", new QuarantineRequest(true), jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // --- Demo mode skip verification test ---

    @Test
    void saveCredentials_demo_skipsVerification() {
        Jwt jwt = buildJwtWithRoleAndUserId("SME_ADMIN");
        when(dataSourceProps.getMode()).thenReturn("demo");
        var request = new hu.riskguard.datasource.api.dto.NavCredentialRequest(
                "testLogin", "rawPass", "signingKey", "exchangeKey", "12345678");
        when(authService.hashPassword("rawPass")).thenReturn("HASHXXX");
        when(aesFieldEncryptor.encrypt(anyString())).thenReturn("enc");

        controller.saveCredentials(request, jwt);

        verify(authService, never()).verifyCredentials(any());
        verify(navTenantCredentialRepository).upsert(eq(TENANT_ID), anyString(), anyString(), anyString(), anyString(), eq("12345678"));
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
