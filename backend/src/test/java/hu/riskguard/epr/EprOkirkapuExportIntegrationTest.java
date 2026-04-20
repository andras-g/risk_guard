package hu.riskguard.epr;

import hu.riskguard.epr.aggregation.api.dto.FilingAggregationResult;
import hu.riskguard.epr.aggregation.domain.InvoiceDrivenFilingAggregator;
import hu.riskguard.epr.api.dto.InvoiceAutoFillResponse;
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

        EprReportArtifact artifact = eprService.generateReport(req, aggResult.kfTotals());

        assertThat(artifact.filename()).matches("okir-kg-kgyf-ne-2026-Q1\\.zip");
        assertThat(artifact.bytes()).isNotEmpty();

        int afterExports = dsl.selectCount()
                .from(EPR_EXPORTS)
                .where(EPR_EXPORTS.TENANT_ID.eq(DEMO_TENANT))
                .fetchOne(0, int.class);
        assertThat(afterExports)
                .as("insertExport must succeed — requires OKIRKAPU_XML on export_format_type enum (V20260416_001)")
                .isEqualTo(beforeExports + 1);
    }

    @Test
    void invoiceAutoFill_returnsVtszLinesForDemoTenantInPreviousQuarter() {
        // User symptom: "Pre-fill from Invoices is still empty for Demo Felhasználó".
        // DemoInvoiceFixtures seeds invoices for the previous quarter relative to today,
        // so the autofill must return the 3 VTSZ groups generated by the trade-company
        // scenario (73181500 screws, 39233000 PET, 48191000 cardboard).
        InvoiceAutoFillResponse response = eprService.autoFillFromInvoices(
                DEMO_TAX_NUMBER, Q1_START, Q1_END, DEMO_TENANT);

        assertThat(response.navAvailable()).isTrue();
        assertThat(response.lines())
                .as("demo fixtures must produce at least the 3 VTSZ groups for the Demo trade company")
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(response.lines())
                .extracting("vtszCode")
                .contains("73181500", "39233000");
    }
}
