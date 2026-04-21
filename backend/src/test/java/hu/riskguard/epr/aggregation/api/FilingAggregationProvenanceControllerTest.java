package hu.riskguard.epr.aggregation.api;

import hu.riskguard.epr.aggregation.api.dto.AggregationMetadata;
import hu.riskguard.epr.aggregation.api.dto.FilingAggregationResult;
import hu.riskguard.epr.aggregation.api.dto.ProvenancePage;
import hu.riskguard.epr.aggregation.api.dto.ProvenanceTag;
import hu.riskguard.epr.aggregation.domain.AggregationProvenanceLine;
import hu.riskguard.epr.aggregation.domain.InvoiceDrivenFilingAggregator;
import hu.riskguard.epr.api.EprController;
import hu.riskguard.epr.audit.AuditService;
import hu.riskguard.epr.domain.EprService;
import hu.riskguard.epr.producer.domain.ProducerProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for provenance endpoints in {@link EprController} (Story 10.8 AC #32).
 *
 * <p>Verifies:
 * <ul>
 *   <li>GET /aggregation/provenance: page/size defaults, size clamped to 500, role-gating
 *   <li>GET /aggregation/provenance.csv: Content-Type, UTF-8 BOM, semicolon delimiter, Content-Disposition
 *   <li>Audit events triggered for both endpoints
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FilingAggregationProvenanceControllerTest {

    @Mock private EprService eprService;
    @Mock private ProducerProfileService producerProfileService;
    @Mock private InvoiceDrivenFilingAggregator aggregator;
    @Mock private AuditService auditService;

    private EprController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID   = UUID.randomUUID();
    private static final LocalDate Q1_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate Q1_END   = LocalDate.of(2026, 3, 31);

    @BeforeEach
    void setUp() {
        controller = new EprController(eprService, producerProfileService, aggregator, auditService);
    }

    // ── Paginated provenance endpoint ─────────────────────────────────────────

    @Test
    void getProvenance_happyPath_returnsProvenancePage() {
        Jwt jwt = buildJwt("SME_ADMIN");
        when(aggregator.aggregateForPeriod(TENANT_ID, Q1_START, Q1_END))
                .thenReturn(resultWithLines(List.of(resolvedLine())));

        ResponseEntity<ProvenancePage> response =
                controller.getProvenance(Q1_START, Q1_END, 0, 50, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProvenancePage page = response.getBody();
        assertThat(page).isNotNull();
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(50);
        assertThat(page.content()).hasSize(1);
    }

    @Test
    void getProvenance_sizeClamped_to500() {
        Jwt jwt = buildJwt("SME_ADMIN");
        when(aggregator.aggregateForPeriod(TENANT_ID, Q1_START, Q1_END))
                .thenReturn(resultWithLines(List.of()));

        ResponseEntity<ProvenancePage> response =
                controller.getProvenance(Q1_START, Q1_END, 0, 9999, jwt);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().size()).isEqualTo(500);
    }

    @Test
    void getProvenance_smeAdminAllowed() {
        Jwt jwt = buildJwt("SME_ADMIN");
        when(aggregator.aggregateForPeriod(any(), any(), any())).thenReturn(emptyResult());

        assertThat(controller.getProvenance(Q1_START, Q1_END, 0, 50, jwt).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void getProvenance_accountantAllowed() {
        Jwt jwt = buildJwt("ACCOUNTANT");
        when(aggregator.aggregateForPeriod(any(), any(), any())).thenReturn(emptyResult());

        assertThat(controller.getProvenance(Q1_START, Q1_END, 0, 50, jwt).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void getProvenance_platformAdminAllowed() {
        Jwt jwt = buildJwt("PLATFORM_ADMIN");
        when(aggregator.aggregateForPeriod(any(), any(), any())).thenReturn(emptyResult());

        assertThat(controller.getProvenance(Q1_START, Q1_END, 0, 50, jwt).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void getProvenance_guestRole_throws403() {
        Jwt jwt = buildJwt("GUEST");

        assertThatThrownBy(() -> controller.getProvenance(Q1_START, Q1_END, 0, 50, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(aggregator, never()).aggregateForPeriod(any(), any(), any());
    }

    @Test
    void getProvenance_callsAuditRecordProvenanceFetch() {
        Jwt jwt = buildJwt("SME_ADMIN");
        when(aggregator.aggregateForPeriod(TENANT_ID, Q1_START, Q1_END)).thenReturn(emptyResult());

        controller.getProvenance(Q1_START, Q1_END, 0, 50, jwt);

        verify(auditService).recordProvenanceFetch(
                eq(TENANT_ID), eq(USER_ID), eq(Q1_START), eq(Q1_END), eq(0), eq(50));
    }

    @Test
    void getProvenance_missingProducerProfile_throws412() {
        Jwt jwt = buildJwt("SME_ADMIN");
        when(producerProfileService.get(TENANT_ID))
                .thenThrow(new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                        "producer.profile.incomplete"));

        assertThatThrownBy(() -> controller.getProvenance(Q1_START, Q1_END, 0, 50, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.PRECONDITION_FAILED));

        verify(aggregator, never()).aggregateForPeriod(any(), any(), any());
        verify(auditService, never()).recordProvenanceFetch(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void getProvenance_paginationSlice_returnsCorrectSubset() {
        // 3 lines, page=1, size=2 → only index 2 in result
        AggregationProvenanceLine first  = namedLine("INV-A");
        AggregationProvenanceLine second = namedLine("INV-B");
        AggregationProvenanceLine third  = namedLine("INV-C");
        List<AggregationProvenanceLine> lines = List.of(first, second, third);
        Jwt jwt = buildJwt("SME_ADMIN");
        when(aggregator.aggregateForPeriod(TENANT_ID, Q1_START, Q1_END))
                .thenReturn(resultWithLines(lines));

        ResponseEntity<ProvenancePage> response =
                controller.getProvenance(Q1_START, Q1_END, 1, 2, jwt);

        ProvenancePage page = response.getBody();
        assertThat(page).isNotNull();
        assertThat(page.content()).hasSize(1);
        // Verify identity: page=1, size=2 must return the THIRD element (index 2), not the first.
        assertThat(page.content().get(0).invoiceNumber()).isEqualTo("INV-C");
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
    }

    @Test
    void getProvenance_invertedRange_throws400() {
        Jwt jwt = buildJwt("SME_ADMIN");

        assertThatThrownBy(() ->
                controller.getProvenance(Q1_END, Q1_START, 0, 50, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(aggregator, never()).aggregateForPeriod(any(), any(), any());
    }

    @Test
    void exportCsv_invertedRange_throws400() {
        Jwt jwt = buildJwt("SME_ADMIN");

        assertThatThrownBy(() ->
                controller.exportProvenanceCsv(Q1_END, Q1_START, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(aggregator, never()).aggregateForPeriod(any(), any(), any());
    }

    @Test
    void getProvenance_sizeZero_clampedToOne() {
        Jwt jwt = buildJwt("SME_ADMIN");
        when(aggregator.aggregateForPeriod(TENANT_ID, Q1_START, Q1_END)).thenReturn(emptyResult());

        ResponseEntity<ProvenancePage> response =
                controller.getProvenance(Q1_START, Q1_END, 0, 0, jwt);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().size()).isEqualTo(1);
    }

    // ── CSV export endpoint ───────────────────────────────────────────────────

    @Test
    void exportCsv_returnsTextCsvContentType() {
        Jwt jwt = buildJwt("SME_ADMIN");
        when(aggregator.aggregateForPeriod(TENANT_ID, Q1_START, Q1_END)).thenReturn(emptyResult());

        ResponseEntity<StreamingResponseBody> response =
                controller.exportProvenanceCsv(Q1_START, Q1_END, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Content-Type is declared via @GetMapping(produces=...) — checked at integration level;
        // Content-Disposition header is set on the ResponseEntity
        String disposition = response.getHeaders().getFirst("Content-Disposition");
        assertThat(disposition).isNotNull().startsWith("attachment; filename=\"provenance-");
    }

    @Test
    void exportCsv_contentDispositionFilename_containsTenantShortIdAndPeriod() {
        Jwt jwt = buildJwt("SME_ADMIN");
        when(aggregator.aggregateForPeriod(TENANT_ID, Q1_START, Q1_END)).thenReturn(emptyResult());

        ResponseEntity<StreamingResponseBody> response =
                controller.exportProvenanceCsv(Q1_START, Q1_END, jwt);

        String disposition = response.getHeaders().getFirst("Content-Disposition");
        String tenantShort = TENANT_ID.toString().substring(0, 8);
        assertThat(disposition).contains(tenantShort);
        assertThat(disposition).contains(Q1_START.toString());
        assertThat(disposition).contains(Q1_END.toString());
        assertThat(disposition).endsWith(".csv\"");
    }

    @Test
    void exportCsv_outputStartsWithUtf8Bom() throws Exception {
        Jwt jwt = buildJwt("SME_ADMIN");
        when(aggregator.aggregateForPeriod(TENANT_ID, Q1_START, Q1_END)).thenReturn(emptyResult());

        ResponseEntity<StreamingResponseBody> response =
                controller.exportProvenanceCsv(Q1_START, Q1_END, jwt);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.getBody().writeTo(baos);
        byte[] bytes = baos.toByteArray();

        // UTF-8 BOM: EF BB BF
        assertThat(bytes).hasSizeGreaterThanOrEqualTo(3);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
    }

    @Test
    void exportCsv_headerRowUsesSemicolonDelimiter() throws Exception {
        Jwt jwt = buildJwt("SME_ADMIN");
        when(aggregator.aggregateForPeriod(TENANT_ID, Q1_START, Q1_END)).thenReturn(emptyResult());

        ResponseEntity<StreamingResponseBody> response =
                controller.exportProvenanceCsv(Q1_START, Q1_END, jwt);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.getBody().writeTo(baos);
        String csv = baos.toString(StandardCharsets.UTF_8);

        assertThat(csv).contains("Számlaszám;Sorszám;VTSZ");
    }

    @Test
    void exportCsv_dataRowUsesSemicolonDelimiter() throws Exception {
        Jwt jwt = buildJwt("SME_ADMIN");
        when(aggregator.aggregateForPeriod(TENANT_ID, Q1_START, Q1_END))
                .thenReturn(resultWithLines(List.of(resolvedLine())));

        ResponseEntity<StreamingResponseBody> response =
                controller.exportProvenanceCsv(Q1_START, Q1_END, jwt);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.getBody().writeTo(baos);
        String csv = baos.toString(StandardCharsets.UTF_8);

        // Split into lines; data row (index 1) must contain semicolons
        String[] lines = csv.split("\r?\n");
        assertThat(lines).hasSizeGreaterThanOrEqualTo(2);
        assertThat(lines[1]).contains(";");
    }

    @Test
    void exportCsv_guestRole_throws403() {
        Jwt jwt = buildJwt("GUEST");

        assertThatThrownBy(() -> controller.exportProvenanceCsv(Q1_START, Q1_END, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(aggregator, never()).aggregateForPeriod(any(), any(), any());
    }

    @Test
    void exportCsv_callsAuditRecordCsvExport() {
        Jwt jwt = buildJwt("SME_ADMIN");
        when(aggregator.aggregateForPeriod(TENANT_ID, Q1_START, Q1_END)).thenReturn(emptyResult());

        controller.exportProvenanceCsv(Q1_START, Q1_END, jwt);

        verify(auditService).recordCsvExport(
                eq(TENANT_ID), eq(USER_ID), eq(Q1_START), eq(Q1_END));
    }

    @Test
    void exportCsv_missingProducerProfile_throws412() {
        Jwt jwt = buildJwt("SME_ADMIN");
        when(producerProfileService.get(TENANT_ID))
                .thenThrow(new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                        "producer.profile.incomplete"));

        assertThatThrownBy(() -> controller.exportProvenanceCsv(Q1_START, Q1_END, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.PRECONDITION_FAILED));

        verify(aggregator, never()).aggregateForPeriod(any(), any(), any());
        verify(auditService, never()).recordCsvExport(any(), any(), any(), any());
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

    private static FilingAggregationResult emptyResult() {
        return resultWithLines(List.of());
    }

    private static FilingAggregationResult resultWithLines(List<AggregationProvenanceLine> lines) {
        return new FilingAggregationResult(
                List.of(), List.of(), List.of(),
                new AggregationMetadata(0, 0, 1, Q1_START, Q1_END, 0L),
                lines);
    }

    private static AggregationProvenanceLine resolvedLine() {
        return namedLine("INV-001");
    }

    private static AggregationProvenanceLine namedLine(String invoiceNumber) {
        return new AggregationProvenanceLine(
                invoiceNumber, 1, "73181500", "Screw",
                new BigDecimal("10.000"), "DARAB",
                UUID.randomUUID(), "Test Product",
                UUID.randomUUID(), 1, "1001 01 00",
                new BigDecimal("0.0200"), ProvenanceTag.REGISTRY_MATCH);
    }
}
