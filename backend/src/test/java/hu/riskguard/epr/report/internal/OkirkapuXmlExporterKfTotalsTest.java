package hu.riskguard.epr.report.internal;

import hu.riskguard.epr.aggregation.api.dto.KfCodeTotal;
import hu.riskguard.epr.producer.domain.ProducerProfile;
import hu.riskguard.epr.report.EprReportArtifact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Golden round-trip test for {@link OkirkapuXmlExporter} (Story 10.5 AC #12).
 *
 * <p>Feeds a known {@link KfCodeTotal} list → validates that:
 * <ol>
 *   <li>The artifact is produced without exception (implicitly validates against OKIRkapu XSD)
 *   <li>The ZIP contains an XML entry with key elements present (UGYFEL_NEVE, ADOSZAM, KF_TERMEKARAM_KOD)
 *   <li>The filename follows the expected pattern
 * </ol>
 */
class OkirkapuXmlExporterKfTotalsTest {

    private OkirkapuXmlExporter exporter;

    private static final UUID TENANT = UUID.randomUUID();
    private static final LocalDate Q1_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate Q1_END = LocalDate.of(2026, 3, 31);

    @BeforeEach
    void setUp() throws Exception {
        KgKgyfNeMarshaller marshaller = new KgKgyfNeMarshaller();
        marshaller.init();
        exporter = new OkirkapuXmlExporter(marshaller);
    }

    @Test
    void generate_singleKfTotal_producesValidZipWithXml() {
        List<KfCodeTotal> totals = List.of(
                new KfCodeTotal("91010101", "Acél", new BigDecimal("2.500"),
                        new BigDecimal("100.00"), new BigDecimal("250"), 1, false, false)
        );

        assertThatNoException().isThrownBy(
                () -> exporter.generate(totals, testProfile(), Q1_START, Q1_END)
        );
    }

    @Test
    void generate_filename_matchesExpectedPattern() {
        EprReportArtifact artifact = exporter.generate(List.of(), testProfile(), Q1_START, Q1_END);

        assertThat(artifact.filename()).isEqualTo("okir-kg-kgyf-ne-2026-Q1.zip");
    }

    @Test
    void generate_zipContainsXmlEntry() throws Exception {
        EprReportArtifact artifact = exporter.generate(List.of(), testProfile(), Q1_START, Q1_END);

        String xmlEntry = extractXmlFromZip(artifact.bytes());
        assertThat(xmlEntry).isNotNull();
        assertThat(artifact.xmlBytes()).isNotEmpty();
    }

    @Test
    void generate_xmlContainsProducerName() throws Exception {
        EprReportArtifact artifact = exporter.generate(List.of(), testProfile(), Q1_START, Q1_END);

        Document doc = parseXml(artifact.xmlBytes());
        NodeList ugyfelneve = doc.getElementsByTagName("UGYFEL_NEVE");
        assertThat(ugyfelneve.getLength()).isGreaterThan(0);
        assertThat(ugyfelneve.item(0).getTextContent()).isEqualTo("Golden Test Kft.");
    }

    @Test
    void generate_xmlContainsTaxNumber() throws Exception {
        EprReportArtifact artifact = exporter.generate(List.of(), testProfile(), Q1_START, Q1_END);

        Document doc = parseXml(artifact.xmlBytes());
        NodeList adoszam = doc.getElementsByTagName("ADOSZAM");
        assertThat(adoszam.getLength()).isGreaterThan(0);
        assertThat(adoszam.item(0).getTextContent()).isEqualTo("12345678");
    }

    @Test
    void generate_kfTotalMappedToXmlRow() throws Exception {
        List<KfCodeTotal> totals = List.of(
                new KfCodeTotal("91010101", "Acél", new BigDecimal("5.750"),
                        new BigDecimal("100.00"), new BigDecimal("575"), 2, false, false)
        );

        EprReportArtifact artifact = exporter.generate(totals, testProfile(), Q1_START, Q1_END);

        Document doc = parseXml(artifact.xmlBytes());
        // KF code 91010101 split: KF_TERMEKARAM_KOD=91, KF_ANYAGARAM_KOD=01, KF_CSOPORT_KOD=01, KF_KOTELEZETTSEG_KOD=0, KF_SZARMAZAS_KOD=1
        NodeList termekAram = doc.getElementsByTagName("KF_TERMEKARAM_KOD");
        assertThat(termekAram.getLength()).isGreaterThan(0);
        assertThat(termekAram.item(0).getTextContent()).isEqualTo("91");
    }

    @Test
    void generate_summaryTextContainsCompanyName() {
        EprReportArtifact artifact = exporter.generate(List.of(), testProfile(), Q1_START, Q1_END);

        assertThat(artifact.summaryReport())
                .contains("Golden Test Kft.")
                .contains("12345678")
                .contains("2026 Q1");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static ProducerProfile testProfile() {
        return new ProducerProfile(
                UUID.randomUUID(), TENANT, "Golden Test Kft.",
                "HU", "Budapest", "1011", "Fő", "utca", "1",
                "12345678-0909-114-01", "01-09-123456",
                "Test User", "ügyvezető", "HU", "1011", "Budapest", "Fő utca",
                "+36123456789", "golden@test.hu",
                99999, true, false, false, false, "12345678");
    }

    private static String extractXmlFromZip(byte[] zipBytes) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".xml")) {
                    return new String(zis.readAllBytes(), "UTF-8");
                }
            }
        }
        return null;
    }

    private static Document parseXml(byte[] xmlBytes) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));
    }
}
