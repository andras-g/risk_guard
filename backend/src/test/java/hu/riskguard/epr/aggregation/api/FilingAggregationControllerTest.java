package hu.riskguard.epr.aggregation.api;

import hu.riskguard.epr.aggregation.api.dto.AggregationMetadata;
import hu.riskguard.epr.aggregation.api.dto.FilingAggregationResult;
import hu.riskguard.epr.aggregation.domain.InvoiceDrivenFilingAggregator;
import hu.riskguard.epr.producer.domain.ProducerProfileService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FilingAggregationController} per Story 10.5 AC #9.
 */
@ExtendWith(MockitoExtension.class)
class FilingAggregationControllerTest {

    @Mock
    private InvoiceDrivenFilingAggregator aggregator;

    @Mock
    private ProducerProfileService producerProfileService;

    private FilingAggregationController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final LocalDate Q1_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate Q1_END = LocalDate.of(2026, 3, 31);

    @BeforeEach
    void setUp() {
        controller = new FilingAggregationController(aggregator, producerProfileService);
    }

    @Test
    void happyPath_returnsAggregationResult() {
        Jwt jwt = buildJwt(TENANT_ID, "SME_ADMIN");
        FilingAggregationResult expected = emptyResult();
        when(aggregator.aggregateForPeriod(TENANT_ID, Q1_START, Q1_END)).thenReturn(expected);
        HttpServletResponse response = mock(HttpServletResponse.class);

        ResponseEntity<?> result = controller.aggregate(Q1_START, Q1_END, jwt, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(expected);
        verify(response).setHeader("Cache-Control", "max-age=60, private");
    }

    @Test
    void invalidPeriod_fromAfterTo_returns400() {
        Jwt jwt = buildJwt(TENANT_ID, "SME_ADMIN");
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertThatThrownBy(() -> controller.aggregate(Q1_END, Q1_START, jwt, response))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(aggregator, never()).aggregateForPeriod(any(), any(), any());
    }

    @Test
    void incompleteProducerProfile_propagates412() {
        Jwt jwt = buildJwt(TENANT_ID, "SME_ADMIN");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(producerProfileService.get(TENANT_ID)).thenThrow(
                new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "profile incomplete"));

        assertThatThrownBy(() -> controller.aggregate(Q1_START, Q1_END, jwt, response))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.PRECONDITION_FAILED));

        verify(aggregator, never()).aggregateForPeriod(any(), any(), any());
    }

    @Test
    void accountantRole_isAllowed() {
        Jwt jwt = buildJwt(TENANT_ID, "ACCOUNTANT");
        when(aggregator.aggregateForPeriod(TENANT_ID, Q1_START, Q1_END)).thenReturn(emptyResult());
        HttpServletResponse response = mock(HttpServletResponse.class);

        ResponseEntity<?> result = controller.aggregate(Q1_START, Q1_END, jwt, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void guestRole_throws403() {
        // AC #19: 403 on wrong role (GUEST)
        Jwt jwt = buildJwt(TENANT_ID, "GUEST");
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertThatThrownBy(() -> controller.aggregate(Q1_START, Q1_END, jwt, response))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(aggregator, never()).aggregateForPeriod(any(), any(), any());
    }

    @Test
    void missingTenantId_throws400() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("role", "SME_ADMIN")
                .build();
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertThatThrownBy(() -> controller.aggregate(Q1_START, Q1_END, jwt, response))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("active_tenant_id");
    }

    @Test
    void fromEqualToTo_isValid_returnsResult() {
        Jwt jwt = buildJwt(TENANT_ID, "PLATFORM_ADMIN");
        LocalDate sameDay = LocalDate.of(2026, 1, 15);
        when(aggregator.aggregateForPeriod(eq(TENANT_ID), eq(sameDay), eq(sameDay)))
                .thenReturn(emptyResult());
        HttpServletResponse response = mock(HttpServletResponse.class);

        ResponseEntity<?> result = controller.aggregate(sameDay, sameDay, jwt, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static Jwt buildJwt(UUID tenantId, String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("active_tenant_id", tenantId.toString())
                .claim("role", role)
                .build();
    }

    private static FilingAggregationResult emptyResult() {
        return new FilingAggregationResult(
                List.of(), List.of(), List.of(),
                new AggregationMetadata(0, 0, 1, Q1_START, Q1_END, 0L),
                List.of());
    }
}
