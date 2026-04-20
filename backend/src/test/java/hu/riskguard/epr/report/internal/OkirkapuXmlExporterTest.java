package hu.riskguard.epr.report.internal;

import hu.riskguard.epr.aggregation.api.dto.KfCodeTotal;
import hu.riskguard.epr.producer.domain.ProducerProfile;
import hu.riskguard.epr.report.EprReportArtifact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OkirkapuXmlExporter} as a pure marshalling concern.
 * Verifies KfCodeTotal → KfCodeAggregate mapping and ZIP/summary generation.
 * Per ADR-0002: the exporter receives pre-computed totals from InvoiceDrivenFilingAggregator.
 */
@ExtendWith(MockitoExtension.class)
class OkirkapuXmlExporterTest {

    @Mock
    private KgKgyfNeMarshaller marshaller;

    private OkirkapuXmlExporter exporter;

    private static final UUID TENANT = UUID.randomUUID();
    private static final LocalDate Q1_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate Q1_END = LocalDate.of(2026, 3, 31);

    @BeforeEach
    void setUp() {
        exporter = new OkirkapuXmlExporter(marshaller);
        when(marshaller.marshal(any(), any(), any(), any())).thenReturn("<xml/>".getBytes());
    }

    @Test
    void emptyTotals_producesValidArtifactWithEmptySummary() {
        EprReportArtifact result = exporter.generate(List.of(), profile(), Q1_START, Q1_END);

        assertThat(result).isNotNull();
        assertThat(result.filename()).isEqualTo("okir-kg-kgyf-ne-2026-Q1.zip");
        assertThat(result.bytes()).isNotEmpty();
        assertThat(result.xmlBytes()).isEqualTo("<xml/>".getBytes());
        assertThat(result.summaryReport()).contains("Nulla bejelentés");
        assertThat(result.provenanceLines()).isEmpty();
    }

    @Test
    void singleKfTotal_mappedCorrectlyToAggregate() {
        KfCodeTotal total = new KfCodeTotal("11010101", "PET palack", new BigDecimal("2.500"),
                new BigDecimal("100.00"), new BigDecimal("250.00"), 3, false, false);

        exporter.generate(List.of(total), profile(), Q1_START, Q1_END);

        verify(marshaller).marshal(any(),
                argThat(aggs -> aggs.size() == 1
                        && "11010101".equals(aggs.get(0).kfCode())
                        && aggs.get(0).totalWeightKg().compareTo(new BigDecimal("2.500")) == 0
                        && aggs.get(0).lineCount() == 3),
                eq(Q1_START), eq(Q1_END));
    }

    @Test
    void multipleKfTotals_allMappedPreservingOrder() {
        List<KfCodeTotal> totals = List.of(
                new KfCodeTotal("11010101", "PET", new BigDecimal("2.500"),
                        new BigDecimal("100"), new BigDecimal("250"), 3, false, false),
                new KfCodeTotal("41010201", "Papír", new BigDecimal("1.200"),
                        new BigDecimal("80"), new BigDecimal("96"), 2, false, false)
        );

        exporter.generate(totals, profile(), Q1_START, Q1_END);

        verify(marshaller).marshal(any(),
                argThat(aggs -> aggs.size() == 2
                        && "11010101".equals(aggs.get(0).kfCode())
                        && "41010201".equals(aggs.get(1).kfCode())),
                eq(Q1_START), eq(Q1_END));
    }

    @Test
    void hasFallback_warningAppearsInSummary() {
        KfCodeTotal total = new KfCodeTotal("11010101", "PET", new BigDecimal("1.000"),
                new BigDecimal("100"), new BigDecimal("100"), 1, true, false);

        EprReportArtifact result = exporter.generate(List.of(total), profile(), Q1_START, Q1_END);

        assertThat(result.summaryReport()).contains("VTSZ visszaesési");
    }

    @Test
    void hasOverflowWarning_warningAppearsInSummary() {
        KfCodeTotal total = new KfCodeTotal("11010101", "PET", new BigDecimal("200000000"),
                new BigDecimal("100"), new BigDecimal("20000000000"), 1, false, true);

        EprReportArtifact result = exporter.generate(List.of(total), profile(), Q1_START, Q1_END);

        assertThat(result.summaryReport()).contains("Rendkívüli súlyérték");
    }

    @Test
    void filename_includesYearAndQuarter() {
        EprReportArtifact result = exporter.generate(List.of(), profile(),
                LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31));

        assertThat(result.filename()).isEqualTo("okir-kg-kgyf-ne-2025-Q4.zip");
    }

    @Test
    void summaryText_includesTaxNumberAndLegalName() {
        EprReportArtifact result = exporter.generate(List.of(), profile(), Q1_START, Q1_END);

        assertThat(result.summaryReport())
                .contains("Test Kft.")
                .contains("12345678");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ProducerProfile profile() {
        return new ProducerProfile(
                UUID.randomUUID(), TENANT, "Test Kft.",
                "HU", "Budapest", "1011", "Fő", "utca", "1",
                "12345678-0909-114-01", "01-09-123456",
                "Test User", "ügyvezető", "HU", "1011", "Budapest", "Fő utca",
                "+36123456789", "test@test.hu",
                12345, true, false, false, false, "12345678");
    }
}
