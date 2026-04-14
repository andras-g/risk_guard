package hu.riskguard.epr;

import hu.riskguard.epr.api.EprAdminController;
import hu.riskguard.epr.api.dto.EprConfigPublishRequest;
import hu.riskguard.epr.api.dto.EprConfigPublishResponse;
import hu.riskguard.epr.api.dto.EprConfigResponse;
import hu.riskguard.epr.api.dto.EprConfigValidateRequest;
import hu.riskguard.epr.api.dto.EprConfigValidateResponse;
import hu.riskguard.epr.domain.EprService;
import hu.riskguard.epr.registry.api.dto.ClassifierUsageSummaryResponse;
import hu.riskguard.epr.registry.domain.ClassifierUsageSummary;
import hu.riskguard.epr.registry.domain.ClassifierUsageService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EprAdminController}.
 * Pure Mockito — no Spring context. Follows DataSourceAdminControllerTest pattern.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EprAdminControllerTest {

    @Mock
    private EprService eprService;

    @Mock
    private ClassifierUsageService classifierUsageService;

    private EprAdminController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new EprAdminController(eprService, classifierUsageService);
    }

    // ─── GET /config ──────────────────────────────────────────────────────────

    @Test
    void platformAdmin_getConfig_returns200WithVersionAndConfigData() {
        Jwt jwt = buildPlatformAdminJwt();
        EprConfigResponse response = new EprConfigResponse(1, "{\"fee_rates\": {}}", Instant.parse("2026-01-01T00:00:00Z"));
        when(eprService.getActiveConfigFull()).thenReturn(response);

        EprConfigResponse result = controller.getConfig(jwt);

        assertThat(result.version()).isEqualTo(1);
        assertThat(result.configData()).isEqualTo("{\"fee_rates\": {}}");
        assertThat(result.activatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void smeAdmin_getConfig_returns403() {
        Jwt jwt = buildSmeAdminJwt();

        assertThatThrownBy(() -> controller.getConfig(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(eprService, never()).getActiveConfigFull();
    }

    @Test
    void accountant_getConfig_returns403() {
        Jwt jwt = buildAccountantJwt();

        assertThatThrownBy(() -> controller.getConfig(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(eprService, never()).getActiveConfigFull();
    }

    // ─── POST /config/validate ────────────────────────────────────────────────

    @Test
    void validate_platformAdmin_validJson_returns200Valid() {
        Jwt jwt = buildPlatformAdminJwt();
        EprConfigValidateRequest req = new EprConfigValidateRequest("{\"valid\": true}");
        when(eprService.validateNewConfig(anyString())).thenReturn(EprConfigValidateResponse.ok());

        EprConfigValidateResponse result = controller.validate(req, jwt);

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void validate_platformAdmin_invalidJson_returns200WithErrors() {
        Jwt jwt = buildPlatformAdminJwt();
        EprConfigValidateRequest req = new EprConfigValidateRequest("not-json");
        List<String> errors = List.of("Invalid JSON: Unrecognized token 'not'");
        when(eprService.validateNewConfig(anyString())).thenReturn(EprConfigValidateResponse.failed(errors));

        EprConfigValidateResponse result = controller.validate(req, jwt);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).startsWith("Invalid JSON");
    }

    @Test
    void smeAdmin_validate_returns403() {
        Jwt jwt = buildSmeAdminJwt();

        assertThatThrownBy(() -> controller.validate(new EprConfigValidateRequest("{}"), jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(eprService, never()).validateNewConfig(anyString());
    }

    @Test
    void accountant_validate_returns403() {
        Jwt jwt = buildAccountantJwt();

        assertThatThrownBy(() -> controller.validate(new EprConfigValidateRequest("{}"), jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(eprService, never()).validateNewConfig(anyString());
    }

    // ─── POST /config/publish ─────────────────────────────────────────────────

    @Test
    void publish_platformAdmin_returns200WithNewVersion() {
        Jwt jwt = buildPlatformAdminJwtWithUserId();
        EprConfigPublishRequest req = new EprConfigPublishRequest("{\"fee_rates\": {}}");
        Instant now = Instant.now();
        when(eprService.publishNewConfig(anyString(), any(UUID.class)))
                .thenReturn(new EprConfigPublishResponse(2, now));

        EprConfigPublishResponse result = controller.publish(req, jwt);

        assertThat(result.version()).isEqualTo(2);
        assertThat(result.activatedAt()).isEqualTo(now);
        verify(eprService).publishNewConfig("{\"fee_rates\": {}}", USER_ID);
    }

    @Test
    void smeAdmin_publish_returns403() {
        Jwt jwt = buildSmeAdminJwt();

        assertThatThrownBy(() -> controller.publish(new EprConfigPublishRequest("{}"), jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(eprService, never()).publishNewConfig(anyString(), any(UUID.class));
    }

    @Test
    void accountant_publish_returns403() {
        Jwt jwt = buildAccountantJwt();

        assertThatThrownBy(() -> controller.publish(new EprConfigPublishRequest("{}"), jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(eprService, never()).publishNewConfig(anyString(), any(UUID.class));
    }

    // ─── GUEST role returns 403 on all endpoints ──────────────────────────────

    @Test
    void guest_getConfig_returns403() {
        Jwt jwt = buildGuestJwt();

        assertThatThrownBy(() -> controller.getConfig(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(eprService, never()).getActiveConfigFull();
    }

    @Test
    void guest_validate_returns403() {
        Jwt jwt = buildGuestJwt();

        assertThatThrownBy(() -> controller.validate(new EprConfigValidateRequest("{}"), jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(eprService, never()).validateNewConfig(anyString());
    }

    @Test
    void guest_publish_returns403() {
        Jwt jwt = buildGuestJwt();

        assertThatThrownBy(() -> controller.publish(new EprConfigPublishRequest("{}"), jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(eprService, never()).publishNewConfig(anyString(), any(UUID.class));
    }

    // ─── GET /classifier/usage ────────────────────────────────────────────────

    @Test
    void platformAdmin_getClassifierUsage_returns200WithSummaryList() {
        Jwt jwt = buildPlatformAdminJwt();
        List<ClassifierUsageSummary> summaries = List.of(
                new ClassifierUsageSummary(TENANT_ID, "Tenant A", 42, 42 * 0.15),
                new ClassifierUsageSummary(UUID.randomUUID(), "Tenant B", 7, 7 * 0.15)
        );
        when(classifierUsageService.getAllTenantsUsage()).thenReturn(summaries);

        List<ClassifierUsageSummaryResponse> result = controller.getClassifierUsage(jwt);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).callCount()).isEqualTo(42);
        assertThat(result.get(1).callCount()).isEqualTo(7);
    }

    @Test
    void smeAdmin_getClassifierUsage_returns403() {
        Jwt jwt = buildSmeAdminJwt();

        assertThatThrownBy(() -> controller.getClassifierUsage(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(classifierUsageService, never()).getAllTenantsUsage();
    }

    // ─── JWT builder helpers ──────────────────────────────────────────────────

    private Jwt buildPlatformAdminJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("platform@test.com")
                .claim("role", "PLATFORM_ADMIN")
                .claim("active_tenant_id", TENANT_ID.toString())
                .build();
    }

    private Jwt buildPlatformAdminJwtWithUserId() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("platform@test.com")
                .claim("role", "PLATFORM_ADMIN")
                .claim("active_tenant_id", TENANT_ID.toString())
                .claim("user_id", USER_ID.toString())
                .build();
    }

    private Jwt buildSmeAdminJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("admin@test.com")
                .claim("role", "SME_ADMIN")
                .claim("active_tenant_id", TENANT_ID.toString())
                .build();
    }

    private Jwt buildAccountantJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user@test.com")
                .claim("role", "ACCOUNTANT")
                .claim("active_tenant_id", TENANT_ID.toString())
                .build();
    }

    private Jwt buildGuestJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("guest@test.com")
                .claim("role", "GUEST")
                .claim("active_tenant_id", TENANT_ID.toString())
                .build();
    }
}
