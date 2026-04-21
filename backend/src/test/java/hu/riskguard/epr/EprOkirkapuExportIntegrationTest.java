package hu.riskguard.epr;

import hu.riskguard.epr.aggregation.api.dto.FilingAggregationResult;
import hu.riskguard.epr.aggregation.domain.InvoiceDrivenFilingAggregator;
import hu.riskguard.epr.api.dto.EprSubmissionSummary;
import hu.riskguard.epr.domain.EprService;
import hu.riskguard.epr.producer.domain.ProducerProfile;
import hu.riskguard.epr.producer.domain.ProducerProfileService;
import hu.riskguard.epr.report.EprReportArtifact;
import hu.riskguard.epr.report.EprReportRequest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static hu.riskguard.jooq.Tables.EPR_EXPORTS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for the OKIRkapu XML export path against the real Demo
 * seed (tenant {@code 00000000-0000-4000-b000-000000000001}), running on a fresh Postgres
 * container so Flyway applies all migrations — including V20260416_001 (extends the
 * {@code export_format_type} enum with {@code OKIRKAPU_XML}) and V20260416_002 (widens
 * {@code producer_profiles.ksh_statistical_number} to VARCHAR(20)).
 *
 * <p>Regression guard for the two bugs where preview/export crashed for Demo users:
 * the enum miss caused insertExport to fail <b>after</b> XML generation, and the
 * CHAR(17) column rejected the 20-char KSH string, so the seeded producer profile
 * silently never landed in the DB → {@code ProducerProfileService.get} threw 412.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class EprOkirkapuExportIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private EprService eprService;

    @Autowired
    private InvoiceDrivenFilingAggregator aggregator;

    @Autowired
    private ProducerProfileService producerProfileService;

    @Autowired
    private DSLContext dsl;

    /** Demo Felhasználó tenant — the user who reported the bug. */
    private static final UUID DEMO_TENANT = UUID.fromString("00000000-0000-4000-b000-000000000001");
    /** Demo SME_ADMIN user — seeded in R__demo_data.sql for DEMO_TENANT. */
    private static final UUID DEMO_USER = UUID.fromString("00000000-0000-4000-b000-000000000002");
    private static final String DEMO_TAX_NUMBER = "12345678";
    private static final LocalDate Q1_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate Q1_END = LocalDate.of(2026, 3, 31);

    @Test
    void demoTenantHasCompleteProducerProfileFromSeed() {
        // If V20260416_002 didn't apply, the seed INSERT fails silently and this throws 412.
        ProducerProfile profile = producerProfileService.get(DEMO_TENANT);

        assertThat(profile.legalName()).isEqualTo("Bemutató Kereskedelmi Kft.");
        assertThat(profile.kshStatisticalNumber())
                .as("KSH statistical number must survive the widened VARCHAR(20) column")
                .isEqualTo("12345678-4690-113-01");
        assertThat(profile.contactEmail()).isEqualTo("demo@riskguard.hu");
    }

    @Test
    void previewReport_succeedsForDemoTenantInQ1_2026() {
        EprReportRequest req = new EprReportRequest(DEMO_TENANT, Q1_START, Q1_END, DEMO_TAX_NUMBER);
        FilingAggregationResult aggResult = aggregator.aggregateForPeriod(DEMO_TENANT, Q1_START, Q1_END);

        EprReportArtifact artifact = eprService.previewReport(req, aggResult.kfTotals());

        assertThat(artifact).isNotNull();
        assertThat(artifact.xmlBytes())
                .as("preview must return rendered XML bytes, not just the ZIP envelope")
                .isNotEmpty();
    }

    @Test
    void generateReport_succeedsAndLogsExportWithOkirkapuXmlEnumForDemoTenant() {
        int beforeExports = dsl.selectCount()
                .from(EPR_EXPORTS)
                .where(EPR_EXPORTS.TENANT_ID.eq(DEMO_TENANT))
                .fetchOne(0, int.class);

        EprReportRequest req = new EprReportRequest(DEMO_TENANT, Q1_START, Q1_END, DEMO_TAX_NUMBER);
        FilingAggregationResult aggResult = aggregator.aggregateForPeriod(DEMO_TENANT, Q1_START, Q1_END);

        var genResult = eprService.generateReport(req, aggResult.kfTotals());

        assertThat(genResult.artifact().filename()).matches("okir-kg-kgyf-ne-2026-Q1\\.zip");
        assertThat(genResult.artifact().bytes()).isNotEmpty();

        int afterExports = dsl.selectCount()
                .from(EPR_EXPORTS)
                .where(EPR_EXPORTS.TENANT_ID.eq(DEMO_TENANT))
                .fetchOne(0, int.class);
        assertThat(afterExports)
                .as("insertExport must succeed — requires OKIRKAPU_XML on export_format_type enum (V20260416_001)")
                .isEqualTo(beforeExports + 1);
    }

    /**
     * AC #24 round-trip: export → list → download against a real Postgres. Asserts the full
     * submission history lifecycle: generateReport persists all 5 Story 10.9 columns;
     * listSubmissions surfaces the row with has_xml_content=true; getSubmissionXmlContent
     * returns the exact XML bytes embedded in the ZIP artifact.
     */
    @Test
    void exportToListToDownloadRoundTrip() {
        EprReportRequest req = new EprReportRequest(
                DEMO_TENANT, Q1_START, Q1_END, DEMO_TAX_NUMBER, DEMO_USER);
        FilingAggregationResult aggResult =
                aggregator.aggregateForPeriod(DEMO_TENANT, Q1_START, Q1_END);

        var genResult = eprService.generateReport(req, aggResult.kfTotals());
        UUID submissionId = genResult.submissionId();

        assertThat(submissionId).isNotNull();

        // DB row has the 5 new columns populated
        var row = dsl.select(
                        EPR_EXPORTS.TOTAL_WEIGHT_KG,
                        EPR_EXPORTS.TOTAL_FEE_HUF,
                        EPR_EXPORTS.XML_CONTENT,
                        EPR_EXPORTS.SUBMITTED_BY_USER_ID,
                        EPR_EXPORTS.FILE_NAME)
                .from(EPR_EXPORTS)
                .where(EPR_EXPORTS.ID.eq(submissionId))
                .fetchOne();
        assertThat(row).isNotNull();
        assertThat(row.get(EPR_EXPORTS.TOTAL_WEIGHT_KG)).isNotNull();
        assertThat(row.get(EPR_EXPORTS.TOTAL_FEE_HUF)).isNotNull();
        assertThat(row.get(EPR_EXPORTS.XML_CONTENT)).isNotNull().isNotEmpty();
        assertThat(row.get(EPR_EXPORTS.SUBMITTED_BY_USER_ID)).isEqualTo(req.submittedByUserId());
        assertThat(row.get(EPR_EXPORTS.FILE_NAME)).isNotBlank();

        // listSubmissions surfaces the new row with has_xml_content=true
        List<EprSubmissionSummary> page = eprService.listSubmissions(DEMO_TENANT, 0, 100);
        EprSubmissionSummary summary = page.stream()
                .filter(s -> s.id().equals(submissionId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("submission not in list: " + submissionId));
        assertThat(summary.hasXmlContent()).isTrue();
        assertThat(summary.periodStart()).isEqualTo(Q1_START);
        assertThat(summary.periodEnd()).isEqualTo(Q1_END);

        // getSubmissionXmlContent returns the same bytes stored in the DB
        Optional<byte[]> xml = eprService.getSubmissionXmlContent(submissionId, DEMO_TENANT);
        assertThat(xml).isPresent();
        assertThat(xml.get()).isEqualTo(row.get(EPR_EXPORTS.XML_CONTENT));
    }

}
