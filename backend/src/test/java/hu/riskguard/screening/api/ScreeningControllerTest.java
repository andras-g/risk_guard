package hu.riskguard.screening.api;

import hu.riskguard.screening.api.dto.PartnerSearchRequest;
import hu.riskguard.screening.api.dto.VerdictResponse;
import hu.riskguard.screening.domain.ScreeningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningControllerTest {

    @Mock
    private ScreeningService screeningService;

    private ScreeningController controller;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new ScreeningController(screeningService);
    }

    @Test
    void searchWithValidTaxNumberShouldReturnVerdictResponse() {
        // Given
        String taxNumber = "12345678";
        PartnerSearchRequest request = new PartnerSearchRequest(taxNumber);
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);

        VerdictResponse expected = VerdictResponse.from(
                UUID.randomUUID(), UUID.randomUUID(), taxNumber,
                "INCOMPLETE", "UNAVAILABLE", OffsetDateTime.now()
        );
        when(screeningService.search(eq(taxNumber), eq(USER_ID), eq(TENANT_ID))).thenReturn(expected);

        // When
        VerdictResponse result = controller.search(request, jwt);

        // Then
        assertThat(result).isEqualTo(expected);
        verify(screeningService).search(taxNumber, USER_ID, TENANT_ID);
    }

    @Test
    void searchWith11DigitTaxNumberShouldReturnVerdictResponse() {
        // Given
        String taxNumber = "12345678901";
        PartnerSearchRequest request = new PartnerSearchRequest(taxNumber);
        Jwt jwt = buildJwt(USER_ID, TENANT_ID);

        VerdictResponse expected = VerdictResponse.from(
                UUID.randomUUID(), UUID.randomUUID(), taxNumber,
                "INCOMPLETE", "UNAVAILABLE", OffsetDateTime.now()
        );
        when(screeningService.search(eq(taxNumber), eq(USER_ID), eq(TENANT_ID))).thenReturn(expected);

        // When
        VerdictResponse result = controller.search(request, jwt);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("INCOMPLETE");
    }

    @Test
    void searchShouldRejectMissingUserId() {
        // Given — JWT without user_id claim
        PartnerSearchRequest request = new PartnerSearchRequest("12345678");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("active_tenant_id", TENANT_ID.toString())
                .build();

        // When / Then
        assertThatThrownBy(() -> controller.search(request, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("user_id");
    }

    @Test
    void searchShouldRejectMissingTenantId() {
        // Given — JWT without active_tenant_id claim
        PartnerSearchRequest request = new PartnerSearchRequest("12345678");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("user_id", USER_ID.toString())
                .build();

        // When / Then
        assertThatThrownBy(() -> controller.search(request, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("active_tenant_id");
    }

    private Jwt buildJwt(UUID userId, UUID tenantId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("user_id", userId.toString())
                .claim("active_tenant_id", tenantId.toString())
                .claim("role", "SME_ADMIN")
                .build();
    }
}
