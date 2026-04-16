package hu.riskguard.epr.report.internal;

import hu.riskguard.datasource.domain.*;
import hu.riskguard.epr.producer.domain.ProducerProfile;
import hu.riskguard.epr.producer.domain.ProducerProfileService;
import hu.riskguard.epr.registry.classifier.ClassificationResult;
import hu.riskguard.epr.registry.classifier.KfCodeClassifierService;
import hu.riskguard.epr.registry.domain.ProductPackagingComponent;
import hu.riskguard.epr.registry.domain.RegistryLookupService;
import hu.riskguard.epr.registry.domain.RegistryMatch;
import hu.riskguard.epr.report.EprReportArtifact;
import hu.riskguard.epr.report.EprReportProvenance;
import hu.riskguard.epr.report.EprReportRequest;
import hu.riskguard.epr.report.ProvenanceTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OkirkapuXmlExporter}'s weight calculation formula.
 * Verifies AC#2: weight = weightPerUnitKg × quantity / unitsPerProduct.
 * Verifies AC#16: backward compatibility (unitsPerProduct=1 produces identical output).
 */
@ExtendWith(MockitoExtension.class)
class OkirkapuXmlExporterTest {

    @Mock private DataSourceService dataSourceService;
    @Mock private RegistryLookupService registryLookupService;
    @Mock private KfCodeClassifierService classifierService;
    @Mock private KgKgyfNeAggregator aggregator;
    @Mock private KgKgyfNeMarshaller marshaller;
    @Mock private ProducerProfileService producerProfileService;

    private OkirkapuXmlExporter exporter;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final String TAX = "12345678";
    private static final LocalDate Q1_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate Q1_END = LocalDate.of(2026, 3, 31);

    @BeforeEach
    void setUp() {
        exporter = new OkirkapuXmlExporter(
                dataSourceService, registryLookupService, classifierService,
                aggregator, marshaller, producerProfileService);

        when(producerProfileService.get(TENANT)).thenReturn(testProfile());
        when(marshaller.marshal(any(), any(), any(), any())).thenReturn("<xml/>".getBytes());
    }

    /**
     * AC#16: Primary packaging with unitsPerProduct=1 produces weight = weightPerUnitKg × quantity
     * (same formula as before the ratio feature).
     */
    @Test
    void primaryOnly_ratio1_sameAsOriginalFormula() {
        ProductPackagingComponent comp = component("11010101", "0.025", 1);
        setupSingleInvoiceLine("PET palack", "39239090", "100", comp);

        EprReportArtifact result = exporter.generate(request());

        // 0.025 × 100 / 1 = 2.500000
        assertProvenanceWeight(result, "11010101", "2.500000");
    }

    /**
     * AC#2: Secondary packaging (6-pack) with unitsPerProduct=6.
     * 100 bottles sold, 6-pack box weighs 0.050 kg → 100 × 0.050 / 6 = 0.833333
     */
    @Test
    void secondaryPackaging_ratio6_correctDivision() {
        ProductPackagingComponent comp = component("41010201", "0.050", 6);
        setupSingleInvoiceLine("Karton multipack", "48195100", "100", comp);

        EprReportArtifact result = exporter.generate(request());

        // 0.050 × 100 / 6 = 0.833333 (6 decimal places, HALF_UP)
        assertProvenanceWeight(result, "41010201", "0.833333");
    }

    /**
     * AC#2: Tertiary packaging (pallet) with unitsPerProduct=480.
     * 480 bottles sold, pallet wrap weighs 0.300 kg → 480 × 0.300 / 480 = 0.300000
     */
    @Test
    void tertiaryPackaging_ratio480_correctDivision() {
        ProductPackagingComponent comp = component("52010101", "0.300", 480);
        setupSingleInvoiceLine("Raklapfólia", "39201000", "480", comp);

        EprReportArtifact result = exporter.generate(request());

        // 0.300 × 480 / 480 = 0.300000
        assertProvenanceWeight(result, "52010101", "0.300000");
    }

    /**
     * AC#2 + AC#16: Product with multiple components (primary + secondary).
     * Verifies each component uses its own unitsPerProduct.
     */
    @Test
    void multiComponent_eachUsesOwnRatio() {
        ProductPackagingComponent primary = component("11010101", "0.025", 1);
        ProductPackagingComponent secondary = component("41010201", "0.050", 6);
        RegistryMatch match = new RegistryMatch(PRODUCT_ID, List.of(primary, secondary));

        setupSingleInvoiceLineWithMatch("PET palack", "39239090", "100", match);

        EprReportArtifact result = exporter.generate(request());

        List<EprReportProvenance> registryHits = result.provenanceLines().stream()
                .filter(p -> p.tag() == ProvenanceTag.REGISTRY_MATCH)
                .toList();

        assertThat(registryHits).hasSize(2);
        // Primary: 0.025 × 100 / 1 = 2.500000
        assertThat(registryHits.get(0).aggregatedWeightKg()).isEqualByComparingTo("2.500000");
        // Secondary: 0.050 × 100 / 6 = 0.833333
        assertThat(registryHits.get(1).aggregatedWeightKg()).isEqualByComparingTo("0.833333");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void setupSingleInvoiceLine(String productName, String vtsz, String qty,
                                         ProductPackagingComponent comp) {
        RegistryMatch match = new RegistryMatch(PRODUCT_ID, List.of(comp));
        setupSingleInvoiceLineWithMatch(productName, vtsz, qty, match);
    }

    private void setupSingleInvoiceLineWithMatch(String productName, String vtsz, String qty,
                                                   RegistryMatch match) {
        InvoiceLineItem line = new InvoiceLineItem(1, productName, new BigDecimal(qty),
                "DARAB", BigDecimal.ONE, new BigDecimal(qty), new BigDecimal(qty),
                vtsz, "VTSZ", vtsz);
        InvoiceSummary summary = new InvoiceSummary("INV-001", "CREATE", TAX, "Supplier",
                "87654321", "Customer", Q1_START, Q1_START,
                new BigDecimal("100000"), "HUF", InvoiceDirection.OUTBOUND);
        InvoiceDetail detail = new InvoiceDetail("INV-001", "CREATE", TAX, "Supplier",
                "87654321", "Customer", Q1_START, Q1_START,
                new BigDecimal("100000"), "HUF", InvoiceDirection.OUTBOUND,
                List.of(line), "TRANSFER", Map.of());

        when(dataSourceService.queryInvoices(TAX, Q1_START, Q1_END, InvoiceDirection.OUTBOUND))
                .thenReturn(new InvoiceQueryResult(List.of(summary), true));
        when(dataSourceService.queryInvoiceDetails("INV-001")).thenReturn(detail);
        when(registryLookupService.findByVtszOrArticleNumber(eq(TENANT), eq(vtsz), any()))
                .thenReturn(Optional.of(match));

        // Aggregator and marshaller pass-through
        when(aggregator.aggregate(any())).thenAnswer(inv -> {
            List<?> contribs = inv.getArgument(0);
            return contribs.stream()
                    .map(c -> (KgKgyfNeAggregator.RegistryWeightContribution) c)
                    .map(c -> new KgKgyfNeAggregator.KfCodeAggregate(c.kfCode(), c.weightKg(), 1))
                    .toList();
        });
    }

    private ProductPackagingComponent component(String kfCode, String weight, int unitsPerProduct) {
        return new ProductPackagingComponent(
                UUID.randomUUID(), PRODUCT_ID, "Material", kfCode,
                new BigDecimal(weight), 0, unitsPerProduct,
                null, null, null, null, null,
                OffsetDateTime.now(), OffsetDateTime.now());
    }

    private EprReportRequest request() {
        return new EprReportRequest(TENANT, Q1_START, Q1_END, TAX);
    }

    private ProducerProfile testProfile() {
        return new ProducerProfile(
                UUID.randomUUID(), TENANT, "Test Kft.",
                "HU", "Budapest", "1011", "Fő", "utca", "1",
                "12345678-0909-114-01", "01-09-123456",
                "Test User", "ügyvezető", "HU", "1011", "Budapest", "Fő utca", "+36123456789", "test@test.hu",
                12345, true, false, false, false, TAX);
    }

    private void assertProvenanceWeight(EprReportArtifact result, String kfCode, String expectedWeight) {
        EprReportProvenance hit = result.provenanceLines().stream()
                .filter(p -> p.tag() == ProvenanceTag.REGISTRY_MATCH && kfCode.equals(p.resolvedKfCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No REGISTRY_MATCH provenance for KF code " + kfCode));
        assertThat(hit.aggregatedWeightKg()).isEqualByComparingTo(expectedWeight);
    }
}
