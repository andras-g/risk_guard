package hu.riskguard.screening.api;

import hu.riskguard.screening.api.dto.AdminAuditPageResponse;
import hu.riskguard.screening.domain.AdminAuditEntry;
import hu.riskguard.screening.domain.ScreeningService;
import hu.riskguard.screening.domain.ScreeningService.AdminAuditPage;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuditAdminController}.
 * Pure Mockito — no Spring context. Follows EprAdminControllerTest pattern.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditAdminControllerTest {

    @Mock
    private ScreeningService screeningService;

    private AuditAdminController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new AuditAdminController(screeningService);
    }

    // ─── smeAdmin + taxNumber ─────────────────────────────────────────────────

    @Test
    void getAuditLog_smeAdmin_taxNumber_returns200WithPagedResults() {
        Jwt jwt = buildSmeAdminJwt();
        AdminAuditEntry entry = buildEntry();
        when(screeningService.getAdminAuditLog("12345678", null, 0, 20))
                .thenReturn(new AdminAuditPage(List.of(entry), 1L, 0, 20));

        AdminAuditPageResponse result = controller.getAuditLog("12345678", null, 0, 20, jwt);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).taxNumber()).isEqualTo("12345678");
    }

    // ─── smeAdmin + tenantId ──────────────────────────────────────────────────

    @Test
    void getAuditLog_smeAdmin_tenantId_returns200WithPagedResults() {
        Jwt jwt = buildSmeAdminJwt();
        AdminAuditEntry entry = buildEntry();
        when(screeningService.getAdminAuditLog(null, TENANT_ID, 0, 20))
                .thenReturn(new AdminAuditPage(List.of(entry), 1L, 0, 20));

        AdminAuditPageResponse result = controller.getAuditLog(null, TENANT_ID, 0, 20, jwt);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).tenantId()).isEqualTo(TENANT_ID.toString());
    }

    // ─── smeAdmin + empty results ──────────────────────────────────────────────

    @Test
    void getAuditLog_smeAdmin_emptyResults_returns200WithEmptyContent() {
        Jwt jwt = buildSmeAdminJwt();
        when(screeningService.getAdminAuditLog("99999999", null, 0, 20))
                .thenReturn(new AdminAuditPage(List.of(), 0L, 0, 20));

        AdminAuditPageResponse result = controller.getAuditLog("99999999", null, 0, 20, jwt);

        assertThat(result.totalElements()).isEqualTo(0);
        assertThat(result.content()).isEmpty();
    }

    // ─── non-admin returns 403 ────────────────────────────────────────────────

    @Test
    void getAuditLog_nonAdmin_returns403() {
        Jwt jwt = buildNonAdminJwt();

        assertThatThrownBy(() -> controller.getAuditLog("12345678", null, 0, 20, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(screeningService, never()).getAdminAuditLog(any(), any(), anyInt(), anyInt());
    }

    // ─── no search criteria returns 400 ──────────────────────────────────────

    @Test
    void getAuditLog_noSearchCriteria_returns400WithProblemDetail() {
        Jwt jwt = buildSmeAdminJwt();

        assertThatThrownBy(() -> controller.getAuditLog(null, null, 0, 20, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("taxNumber or tenantId");
                });

        verify(screeningService, never()).getAdminAuditLog(any(), any(), anyInt(), anyInt());
    }

    // ─── size clamped to 100 max ──────────────────────────────────────────────

    @Test
    void getAuditLog_sizeClamped_to100Max() {
        Jwt jwt = buildSmeAdminJwt();
        when(screeningService.getAdminAuditLog(eq("12345678"), isNull(), eq(0), eq(100)))
                .thenReturn(new AdminAuditPage(List.of(), 0L, 0, 100));

        controller.getAuditLog("12345678", null, 0, 9999, jwt);

        verify(screeningService).getAdminAuditLog("12345678", null, 0, 100);
    }

    // ─── negative page clamped to 0 ──────────────────────────────────────────

    @Test
    void getAuditLog_negativePage_clampsToZero() {
        Jwt jwt = buildSmeAdminJwt();
        when(screeningService.getAdminAuditLog(eq("12345678"), isNull(), eq(0), eq(20)))
                .thenReturn(new AdminAuditPage(List.of(), 0L, 0, 20));

        controller.getAuditLog("12345678", null, -5, 20, jwt);

        verify(screeningService).getAdminAuditLog("12345678", null, 0, 20);
    }

    // ─── combined taxNumber + tenantId passes both params ─────────────────────

    @Test
    void getAuditLog_combinedTaxNumberAndTenantId_passesAllParams() {
        Jwt jwt = buildSmeAdminJwt();
        when(screeningService.getAdminAuditLog("12345678", TENANT_ID, 0, 20))
                .thenReturn(new AdminAuditPage(List.of(), 0L, 0, 20));

        controller.getAuditLog("12345678", TENANT_ID, 0, 20, jwt);

        verify(screeningService).getAdminAuditLog("12345678", TENANT_ID, 0, 20);
    }

    // ─── JWT builder helpers ──────────────────────────────────────────────────

    private Jwt buildSmeAdminJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("admin@test.com")
                .claim("role", "SME_ADMIN")
                .claim("active_tenant_id", TENANT_ID.toString())
                .claim("user_id", USER_ID.toString())
                .build();
    }

    private Jwt buildNonAdminJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user@test.com")
                .claim("role", "PRO_EPR")
                .claim("active_tenant_id", TENANT_ID.toString())
                .build();
    }

    private AdminAuditEntry buildEntry() {
        return new AdminAuditEntry(
                UUID.randomUUID(), TENANT_ID, USER_ID,
                "Test Co", "12345678",
                "RELIABLE", "FRESH",
                OffsetDateTime.now(), "abc123def456abc123def456abc123def456abc123def456abc123def456abcd",
                "DEMO", "MANUAL",
                List.of("https://nav.gov.hu"), "Disclaimer text"
        );
    }
}
