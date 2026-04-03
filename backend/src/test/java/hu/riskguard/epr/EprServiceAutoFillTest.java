package hu.riskguard.epr;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.datasource.domain.InvoiceDetail;
import hu.riskguard.datasource.domain.InvoiceDirection;
import hu.riskguard.datasource.domain.InvoiceLineItem;
import hu.riskguard.datasource.domain.InvoiceQueryResult;
import hu.riskguard.datasource.domain.InvoiceSummary;
import hu.riskguard.epr.api.dto.InvoiceAutoFillResponse;
import hu.riskguard.epr.domain.DagEngine;
import hu.riskguard.epr.domain.EprConfigValidator;
import hu.riskguard.epr.domain.EprService;
import hu.riskguard.epr.domain.FeeCalculator;
import hu.riskguard.epr.domain.MohuExporter;
import hu.riskguard.epr.internal.EprRepository;
import hu.riskguard.jooq.tables.records.EprMaterialTemplatesRecord;
import org.jooq.JSONB;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
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
 * Unit tests for {@link EprService#autoFillFromInvoices}.
 */
@ExtendWith(MockitoExtension.class)
class EprServiceAutoFillTest {

    @Mock
    private EprRepository eprRepository;
    @Mock
    private DagEngine dagEngine;
    @Mock
    private FeeCalculator feeCalculator;
    @Mock
    private MohuExporter mohuExporter;
    @Mock
    private EprConfigValidator eprConfigValidator;
    @Mock
    private DataSourceService dataSourceService;

    private EprService eprService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    // Pre-built mock Records to avoid nested when() inside test helpers
    private final Record standardConfigRecord = buildConfigRecord("""
            {"vtszMappings":[
              {"vtszPrefix":"4819","kfCode":"11010101","materialName_hu":"Karton csomagolás"},
              {"vtszPrefix":"3923","kfCode":"11020101","materialName_hu":"PET csomagolás"},
              {"vtszPrefix":"7214","kfCode":"91010101","materialName_hu":"Acél rúd/profil"}
            ]}""");

    private final Record longerPrefixConfigRecord = buildConfigRecord("""
            {"vtszMappings":[
              {"vtszPrefix":"4819","kfCode":"11010101","materialName_hu":"Karton csomagolás"},
              {"vtszPrefix":"48191","kfCode":"11010199","materialName_hu":"Karton speciális"}
            ]}""");

    @BeforeEach
    void setUp() {
        eprService = new EprService(eprRepository, dagEngine, feeCalculator,
                mohuExporter, eprConfigValidator, dataSourceService);
    }

    @Test
    void autoFillFromInvoices_demoMode_returnsLinesWithVtszGrouping() {
        InvoiceSummary summary1 = buildSummary("INV-001");
        InvoiceSummary summary2 = buildSummary("INV-002");

        InvoiceDetail detail1 = buildDetail("INV-001", List.of(
                buildLineItem("48191000", new BigDecimal("100"), "DARAB"),
                buildLineItem("39233000", new BigDecimal("200"), "DARAB"),
                buildLineItem("72142000", new BigDecimal("50"), "KG")
        ));
        InvoiceDetail detail2 = buildDetail("INV-002", List.of(
                buildLineItem("48191000", new BigDecimal("50"), "DARAB")
        ));

        when(dataSourceService.getMode()).thenReturn("demo");
        when(dataSourceService.getTenantTaxNumber(TENANT_ID)).thenReturn(Optional.empty());
        when(dataSourceService.queryInvoices("12345678", FROM, TO, InvoiceDirection.OUTBOUND))
                .thenReturn(new InvoiceQueryResult(List.of(summary1, summary2), true));
        when(dataSourceService.queryInvoiceDetails("INV-001")).thenReturn(detail1);
        when(dataSourceService.queryInvoiceDetails("INV-002")).thenReturn(detail2);
        when(eprRepository.findActiveConfig()).thenReturn(Optional.of(standardConfigRecord));
        when(eprRepository.findAllByTenant(TENANT_ID)).thenReturn(List.of());

        InvoiceAutoFillResponse response = eprService.autoFillFromInvoices("12345678", FROM, TO, TENANT_ID);

        // AC8: ≥3 distinct lines with non-null suggestedKfCode
        assertThat(response.lines()).hasSize(3);
        assertThat(response.lines()).allMatch(l -> l.suggestedKfCode() != null);
        // VTSZ 48191000 group: 100 + 50 = 150
        var cardboardLine = response.lines().stream()
                .filter(l -> "48191000".equals(l.vtszCode()))
                .findFirst();
        assertThat(cardboardLine).isPresent();
        assertThat(cardboardLine.get().aggregatedQuantity()).isEqualByComparingTo(new BigDecimal("150"));
        assertThat(cardboardLine.get().suggestedKfCode()).isEqualTo("11010101");
        assertThat(response.navAvailable()).isTrue();
    }

    @Test
    void autoFillFromInvoices_vtszLongestPrefixWins() {
        InvoiceSummary summary = buildSummary("INV-100");
        InvoiceDetail detail = buildDetail("INV-100", List.of(
                buildLineItem("48191000", new BigDecimal("10"), "DARAB")
        ));
        when(dataSourceService.getMode()).thenReturn("demo");
        when(dataSourceService.getTenantTaxNumber(TENANT_ID)).thenReturn(Optional.empty());
        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(summary), true));
        when(dataSourceService.queryInvoiceDetails("INV-100")).thenReturn(detail);
        when(eprRepository.findActiveConfig()).thenReturn(Optional.of(longerPrefixConfigRecord));
        when(eprRepository.findAllByTenant(TENANT_ID)).thenReturn(List.of());

        InvoiceAutoFillResponse response = eprService.autoFillFromInvoices("12345678", FROM, TO, TENANT_ID);

        // Should match "48191" (longer prefix) over "4819"
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).suggestedKfCode()).isEqualTo("11010199");
    }

    @Test
    void autoFillFromInvoices_hasExistingTemplate_whenNameMatches() {
        InvoiceSummary summary = buildSummary("INV-200");
        InvoiceDetail detail = buildDetail("INV-200", List.of(
                buildLineItem("48191000", new BigDecimal("100"), "DARAB")
        ));
        when(dataSourceService.getMode()).thenReturn("demo");
        when(dataSourceService.getTenantTaxNumber(TENANT_ID)).thenReturn(Optional.empty());
        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(summary), true));
        when(dataSourceService.queryInvoiceDetails("INV-200")).thenReturn(detail);
        when(eprRepository.findActiveConfig()).thenReturn(Optional.of(standardConfigRecord));

        // A template named exactly "Karton csomagolás" exists
        UUID templateId = UUID.randomUUID();
        EprMaterialTemplatesRecord template = new EprMaterialTemplatesRecord();
        template.setId(templateId);
        template.setName("Karton csomagolás");
        template.setTenantId(TENANT_ID);
        template.setVerified(true);
        template.setRecurring(true);
        template.setCreatedAt(OffsetDateTime.now());
        template.setUpdatedAt(OffsetDateTime.now());
        when(eprRepository.findAllByTenant(TENANT_ID)).thenReturn(List.of(template));

        InvoiceAutoFillResponse response = eprService.autoFillFromInvoices("12345678", FROM, TO, TENANT_ID);

        var line = response.lines().stream()
                .filter(l -> "48191000".equals(l.vtszCode()))
                .findFirst();
        assertThat(line).isPresent();
        assertThat(line.get().hasExistingTemplate()).isTrue();
        assertThat(line.get().existingTemplateId()).isEqualTo(templateId);
    }

    @Test
    void autoFillFromInvoices_navUnavailableOnError() {
        // serviceAvailable=false from queryInvoices → navAvailable should be false
        when(dataSourceService.getMode()).thenReturn("live");
        when(dataSourceService.getTenantTaxNumber(TENANT_ID)).thenReturn(Optional.empty());
        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(), false));
        when(eprRepository.findActiveConfig()).thenReturn(Optional.empty());
        when(eprRepository.findAllByTenant(TENANT_ID)).thenReturn(List.of());

        InvoiceAutoFillResponse response = eprService.autoFillFromInvoices("12345678", FROM, TO, TENANT_ID);

        assertThat(response.lines()).isEmpty();
        assertThat(response.navAvailable()).isFalse();
        assertThat(response.dataSourceMode()).isEqualTo("live");
    }

    @Test
    void autoFillFromInvoices_navAvailableTrue_whenEmptyResultWithServiceUp() {
        // serviceAvailable=true but empty results → navAvailable should be true (no invoices, not an error)
        when(dataSourceService.getMode()).thenReturn("live");
        when(dataSourceService.getTenantTaxNumber(TENANT_ID)).thenReturn(Optional.empty());
        when(dataSourceService.queryInvoices(any(), any(), any(), any()))
                .thenReturn(new InvoiceQueryResult(List.of(), true));
        when(eprRepository.findActiveConfig()).thenReturn(Optional.empty());
        when(eprRepository.findAllByTenant(TENANT_ID)).thenReturn(List.of());

        InvoiceAutoFillResponse response = eprService.autoFillFromInvoices("12345678", FROM, TO, TENANT_ID);

        assertThat(response.lines()).isEmpty();
        assertThat(response.navAvailable()).isTrue();
    }

    @Test
    void autoFillFromInvoices_rejectsMismatchedTaxNumber() {
        // D2: tenant has registered tax number that doesn't match request
        when(dataSourceService.getTenantTaxNumber(TENANT_ID)).thenReturn(Optional.of("99999999"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> eprService.autoFillFromInvoices("12345678", FROM, TO, TENANT_ID)
        );
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private InvoiceSummary buildSummary(String invoiceNumber) {
        return new InvoiceSummary(invoiceNumber, "CREATE", "12345678", "Test Co",
                "87654321", "Customer Co", FROM.plusDays(5), null,
                BigDecimal.ZERO, "HUF", InvoiceDirection.OUTBOUND);
    }

    private InvoiceDetail buildDetail(String invoiceNumber, List<InvoiceLineItem> items) {
        return new InvoiceDetail(invoiceNumber, "CREATE", "12345678", "Test Co",
                "87654321", "Customer Co", FROM.plusDays(5), null,
                BigDecimal.ZERO, "HUF", InvoiceDirection.OUTBOUND,
                items, null, Map.of());
    }

    private InvoiceLineItem buildLineItem(String vtszCode, BigDecimal quantity, String unit) {
        return new InvoiceLineItem(1, "Test item", quantity, unit,
                BigDecimal.valueOf(100), BigDecimal.valueOf(100).multiply(quantity),
                BigDecimal.valueOf(100).multiply(quantity), vtszCode, "VTSZ", vtszCode);
    }

    private static Record buildConfigRecord(String json) {
        Record record = Mockito.mock(Record.class);
        Mockito.when(record.get("config_data")).thenReturn(JSONB.jsonb(json));
        return record;
    }
}
