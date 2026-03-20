package hu.riskguard.notification.api;

import hu.riskguard.notification.api.dto.PortfolioAlertResponse;
import hu.riskguard.notification.domain.NotificationService;
import hu.riskguard.notification.domain.PortfolioAlert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PortfolioController}.
 * Pure Mockito — no Spring context. Follows WatchlistControllerTest pattern.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {

    @Mock
    private NotificationService notificationService;

    private PortfolioController controller;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final String ACCOUNTANT_EMAIL = "accountant@test.com";

    @BeforeEach
    void setUp() {
        controller = new PortfolioController(notificationService);
    }

    @Test
    void accountantGetsAlertsFromMultipleTenants() {
        Jwt jwt = buildAccountantJwt();
        mockUserResolution();

        List<PortfolioAlert> alerts = List.of(
                buildAlert(TENANT_A, "Tenant A", "12345678", "RELIABLE", "AT_RISK"),
                buildAlert(TENANT_B, "Tenant B", "99887766", "AT_RISK", "RELIABLE"));
        when(notificationService.getPortfolioAlerts(eq(USER_ID), eq(7))).thenReturn(alerts);

        List<PortfolioAlertResponse> result = controller.getAlerts(7, jwt);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).taxNumber()).isEqualTo("12345678");
        assertThat(result.get(1).tenantName()).isEqualTo("Tenant B");
        verify(notificationService).getPortfolioAlerts(USER_ID, 7);
    }

    @Test
    void daysZeroReturns400() {
        Jwt jwt = buildAccountantJwt();

        assertThatThrownBy(() -> controller.getAlerts(0, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void daysNegativeReturns400() {
        Jwt jwt = buildAccountantJwt();

        assertThatThrownBy(() -> controller.getAlerts(-5, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void daysOver30Returns400() {
        Jwt jwt = buildAccountantJwt();

        assertThatThrownBy(() -> controller.getAlerts(31, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void smeAdminGets403() {
        Jwt jwt = buildJwtWithRole("SME_ADMIN");

        assertThatThrownBy(() -> controller.getAlerts(7, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void guestGets403() {
        Jwt jwt = buildJwtWithRole("GUEST");

        assertThatThrownBy(() -> controller.getAlerts(7, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void daysAcceptsBoundaryValue1() {
        Jwt jwt = buildAccountantJwt();
        mockUserResolution();
        when(notificationService.getPortfolioAlerts(eq(USER_ID), eq(1))).thenReturn(List.of());

        List<PortfolioAlertResponse> result = controller.getAlerts(1, jwt);

        assertThat(result).isEmpty();
        verify(notificationService).getPortfolioAlerts(USER_ID, 1);
    }

    @Test
    void daysAcceptsBoundaryValue30() {
        Jwt jwt = buildAccountantJwt();
        mockUserResolution();
        when(notificationService.getPortfolioAlerts(eq(USER_ID), eq(30))).thenReturn(List.of());

        List<PortfolioAlertResponse> result = controller.getAlerts(30, jwt);

        assertThat(result).isEmpty();
        verify(notificationService).getPortfolioAlerts(USER_ID, 30);
    }

    @Test
    void daysPassedToService() {
        Jwt jwt = buildAccountantJwt();
        mockUserResolution();
        when(notificationService.getPortfolioAlerts(eq(USER_ID), eq(14))).thenReturn(List.of());

        controller.getAlerts(14, jwt);

        verify(notificationService).getPortfolioAlerts(USER_ID, 14);
    }

    @Test
    void emptyResultReturnsEmptyArray() {
        Jwt jwt = buildAccountantJwt();
        mockUserResolution();
        when(notificationService.getPortfolioAlerts(eq(USER_ID), eq(7))).thenReturn(List.of());

        List<PortfolioAlertResponse> result = controller.getAlerts(7, jwt);

        assertThat(result).isEmpty();
    }

    @Test
    void missingSubClaimReturns401() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("role", "ACCOUNTANT")
                .build();

        assertThatThrownBy(() -> controller.getAlerts(7, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    void unknownEmailReturns401() {
        Jwt jwt = buildAccountantJwt();
        when(notificationService.resolveUserIdByEmail(eq(ACCOUNTANT_EMAIL)))
                .thenThrow(new IllegalArgumentException("User not found for email"));

        assertThatThrownBy(() -> controller.getAlerts(7, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    // --- Helpers ---

    private Jwt buildAccountantJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(ACCOUNTANT_EMAIL)
                .claim("role", "ACCOUNTANT")
                .claim("active_tenant_id", TENANT_A.toString())
                .build();
    }

    private Jwt buildJwtWithRole(String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user@test.com")
                .claim("role", role)
                .claim("active_tenant_id", TENANT_A.toString())
                .build();
    }

    private void mockUserResolution() {
        when(notificationService.resolveUserIdByEmail(eq(ACCOUNTANT_EMAIL))).thenReturn(USER_ID);
    }

    private PortfolioAlert buildAlert(UUID tenantId, String tenantName,
                                       String taxNumber, String prevStatus, String newStatus) {
        return new PortfolioAlert(
                UUID.randomUUID(), tenantId, tenantName, taxNumber,
                "Test Company Kft.", prevStatus, newStatus,
                OffsetDateTime.now(), "abc123hash", UUID.randomUUID());
    }
}
