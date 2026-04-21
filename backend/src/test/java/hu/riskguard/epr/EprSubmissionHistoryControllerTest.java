package hu.riskguard.epr;

import hu.riskguard.epr.aggregation.domain.InvoiceDrivenFilingAggregator;
import hu.riskguard.epr.api.EprController;
import hu.riskguard.epr.api.dto.EprSubmissionPage;
import hu.riskguard.epr.api.dto.EprSubmissionSummary;
import hu.riskguard.epr.audit.AuditService;
import hu.riskguard.epr.domain.EprService;
import hu.riskguard.epr.producer.domain.ProducerProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for submission history endpoints in {@link EprController} (Story 10.9 AC #23).
 */
@ExtendWith(MockitoExtension.class)
class EprSubmissionHistoryControllerTest {

    @Mock private EprService eprService;
    @Mock private ProducerProfileService producerProfileService;
    @Mock private InvoiceDrivenFilingAggregator aggregator;
    @Mock private AuditService auditService;

    private EprController controller;

    private static final UUID TENANT_ID    = UUID.randomUUID();
    private static final UUID USER_ID      = UUID.randomUUID();
    private static final UUID SUBMISSION_ID = UUID.randomUUID();
    private static final LocalDate Q1_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate Q1_END   = LocalDate.of(2026, 3, 31);

    @BeforeEach
    void setUp() {
        controller = new EprController(eprService, producerProfileService, aggregator, auditService);
    }

    @Test
    void listSubmissions_emptyTenant_returnsEmptyPage() {
        when(eprService.listSubmissions(TENANT_ID, 0, 25)).thenReturn(List.of());
        when(eprService.countSubmissions(TENANT_ID)).thenReturn(0L);
        Jwt jwt = buildJwt("SME_ADMIN");

        ResponseEntity<EprSubmissionPage> response = controller.listSubmissions(0, 25, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).isEmpty();
        assertThat(response.getBody().totalElements()).isEqualTo(0L);
    }

    @Test
    void listSubmissions_withSubmission_returnsSummary() {
        EprSubmissionSummary summary = sampleSummary(SUBMISSION_ID);
        when(eprService.listSubmissions(TENANT_ID, 0, 25)).thenReturn(List.of(summary));
        when(eprService.countSubmissions(TENANT_ID)).thenReturn(1L);
        Jwt jwt = buildJwt("SME_ADMIN");

        ResponseEntity<EprSubmissionPage> response = controller.listSubmissions(0, 25, jwt);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().totalElements()).isEqualTo(1L);
        assertThat(response.getBody().content()).hasSize(1);
        assertThat(response.getBody().content().get(0).id()).isEqualTo(SUBMISSION_ID);
    }

    @Test
    void listSubmissions_sizeClampedTo100() {
        when(eprService.listSubmissions(eq(TENANT_ID), eq(0), eq(100))).thenReturn(List.of());
        when(eprService.countSubmissions(TENANT_ID)).thenReturn(0L);
        Jwt jwt = buildJwt("SME_ADMIN");

        ResponseEntity<EprSubmissionPage> response = controller.listSubmissions(0, 500, jwt);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().size()).isEqualTo(100);
        // Verify service was called with clamped size
        verify(eprService).listSubmissions(TENANT_ID, 0, 100);
    }

    @Test
    void listSubmissions_defaultSortByPeriodEndDesc() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        EprSubmissionSummary newer = sampleSummaryForPeriod(id1, LocalDate.of(2026, 3, 31));
        EprSubmissionSummary older = sampleSummaryForPeriod(id2, LocalDate.of(2025, 12, 31));
        when(eprService.listSubmissions(TENANT_ID, 0, 25)).thenReturn(List.of(newer, older));
        when(eprService.countSubmissions(TENANT_ID)).thenReturn(2L);
        Jwt jwt = buildJwt("SME_ADMIN");

        ResponseEntity<EprSubmissionPage> response = controller.listSubmissions(0, 25, jwt);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content().get(0).id()).isEqualTo(id1);
        assertThat(response.getBody().content().get(1).id()).isEqualTo(id2);
    }

    @Test
    void getSubmission_unknownId_returns404() {
        when(eprService.findSubmission(any(), eq(TENANT_ID))).thenReturn(Optional.empty());
        Jwt jwt = buildJwt("SME_ADMIN");

        assertThatThrownBy(() -> controller.getSubmission(UUID.randomUUID(), jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getSubmission_crossTenant_returns404_preventingEnumeration() {
        // Cross-tenant: findSubmission returns empty (backend scopes by tenant_id)
        when(eprService.findSubmission(SUBMISSION_ID, TENANT_ID)).thenReturn(Optional.empty());
        Jwt jwt = buildJwt("SME_ADMIN");

        assertThatThrownBy(() -> controller.getSubmission(SUBMISSION_ID, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void downloadSubmission_xmlContent_returnsBytes() {
        byte[] xmlBytes = "<xml/>".getBytes();
        EprSubmissionSummary summary = sampleSummary(SUBMISSION_ID);
        when(eprService.findSubmission(SUBMISSION_ID, TENANT_ID)).thenReturn(Optional.of(summary));
        when(eprService.getSubmissionXmlContent(SUBMISSION_ID, TENANT_ID)).thenReturn(Optional.of(xmlBytes));
        Jwt jwt = buildJwt("SME_ADMIN");

        ResponseEntity<byte[]> response = controller.downloadSubmission(SUBMISSION_ID, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(xmlBytes);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/xml");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("filename=");
        verify(auditService).recordSubmissionDownload(TENANT_ID, USER_ID, SUBMISSION_ID);
    }

    @Test
    void downloadSubmission_nullXmlContent_returns404() {
        EprSubmissionSummary summary = new EprSubmissionSummary(
                SUBMISSION_ID, Q1_START, Q1_END, null, null,
                OffsetDateTime.now(), null, null, false);
        when(eprService.findSubmission(SUBMISSION_ID, TENANT_ID)).thenReturn(Optional.of(summary));
        when(eprService.getSubmissionXmlContent(SUBMISSION_ID, TENANT_ID)).thenReturn(Optional.empty());
        Jwt jwt = buildJwt("SME_ADMIN");

        assertThatThrownBy(() -> controller.downloadSubmission(SUBMISSION_ID, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Jwt buildJwt(String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user@example.com")
                .claim("active_tenant_id", TENANT_ID.toString())
                .claim("user_id", USER_ID.toString())
                .claim("role", role)
                .build();
    }

    private static EprSubmissionSummary sampleSummary(UUID id) {
        return new EprSubmissionSummary(
                id, Q1_START, Q1_END,
                new BigDecimal("10.000"), new BigDecimal("5000.00"),
                OffsetDateTime.now(), "okirkapu-test.xml",
                "user@example.com", true);
    }

    private static EprSubmissionSummary sampleSummaryForPeriod(UUID id, LocalDate periodEnd) {
        return new EprSubmissionSummary(
                id, periodEnd.withDayOfMonth(1), periodEnd,
                new BigDecimal("10.000"), new BigDecimal("5000.00"),
                OffsetDateTime.now(), "okirkapu-test.xml",
                "user@example.com", true);
    }
}
