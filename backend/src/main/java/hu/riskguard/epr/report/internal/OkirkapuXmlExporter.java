package hu.riskguard.epr.report.internal;

import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.datasource.domain.InvoiceDirection;
import hu.riskguard.datasource.domain.InvoiceLineItem;
import hu.riskguard.datasource.domain.InvoiceQueryResult;
import hu.riskguard.datasource.domain.InvoiceSummary;
import hu.riskguard.epr.producer.domain.ProducerProfile;
import hu.riskguard.epr.producer.domain.ProducerProfileService;
import hu.riskguard.epr.registry.classifier.ClassificationStrategy;
import hu.riskguard.epr.registry.classifier.KfCodeClassifierService;
import hu.riskguard.epr.registry.domain.ProductPackagingComponent;
import hu.riskguard.epr.registry.domain.RegistryLookupService;
import hu.riskguard.epr.registry.domain.RegistryMatch;
import hu.riskguard.epr.report.EprReportArtifact;
import hu.riskguard.epr.report.EprReportProvenance;
import hu.riskguard.epr.report.EprReportRequest;
import hu.riskguard.epr.report.EprReportTarget;
import hu.riskguard.epr.report.ProvenanceTag;
import hu.riskguard.epr.report.internal.KgKgyfNeAggregator.KfCodeAggregate;
import hu.riskguard.epr.report.internal.KgKgyfNeAggregator.RegistryWeightContribution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * OKIRkapu-specific implementation of {@link EprReportTarget}.
 *
 * <p>Activated when {@code riskguard.epr.report.target=okirkapu-xml} (the default, per ADR-0002).
 *
 * <p>Pipeline per {@link #generate(EprReportRequest)}:
 * <ol>
 *   <li>Fetch producer profile (throws 412 if incomplete)</li>
 *   <li>Fetch outbound invoices via {@link DataSourceService}</li>
 *   <li>For each invoice line: registry lookup → weight contributions + provenance</li>
 *   <li>Aggregate by KF code via {@link KgKgyfNeAggregator}</li>
 *   <li>Marshal to XML via {@link KgKgyfNeMarshaller}</li>
 *   <li>Package as .zip (XML + summary .txt)</li>
 * </ol>
 *
 * <p><b>Critical:</b> only REGISTRY_MATCH rows contribute to the XML.
 * VTSZ_FALLBACK and UNMATCHED rows appear in provenance only.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "riskguard.epr.report.target", havingValue = "okirkapu-xml", matchIfMissing = true)
@RequiredArgsConstructor
public class OkirkapuXmlExporter implements EprReportTarget {

    private final DataSourceService dataSourceService;
    private final RegistryLookupService registryLookupService;
    private final KfCodeClassifierService classifierService;
    private final KgKgyfNeAggregator aggregator;
    private final KgKgyfNeMarshaller marshaller;
    private final ProducerProfileService producerProfileService;

    @Override
    public EprReportArtifact generate(EprReportRequest request) {
        UUID tenantId = request.tenantId();
        LocalDate periodStart = request.periodStart();
        LocalDate periodEnd = request.periodEnd();

        // Step 1: load complete producer profile
        ProducerProfile profile = producerProfileService.get(tenantId);

        // Step 2: fetch outbound invoices
        InvoiceQueryResult queryResult = dataSourceService.queryInvoices(
                request.taxNumber(), periodStart, periodEnd, InvoiceDirection.OUTBOUND);
        if (!queryResult.serviceAvailable()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "NAV invoice query failed — cannot generate EPR report");
        }
        List<InvoiceSummary> summaries = queryResult.summaries();
        log.info("OkirkapuXmlExporter: tenant={} period={}/{} invoices={}",
                tenantId, periodStart, periodEnd, summaries.size());

        // Step 3: process each invoice line
        List<RegistryWeightContribution> contributions = new ArrayList<>();
        List<EprReportProvenance> provenanceLines = new ArrayList<>();

        for (InvoiceSummary summary : summaries) {
            var detail = dataSourceService.queryInvoiceDetails(summary.invoiceNumber());
            for (InvoiceLineItem item : detail.lineItems()) {
                processLineItem(tenantId, summary.invoiceNumber(), item,
                        contributions, provenanceLines);
            }
        }

        // Step 4: aggregate by KF code
        List<KfCodeAggregate> aggregates = aggregator.aggregate(contributions);

        // Step 5: marshal to XML
        byte[] xmlBytes = marshaller.marshal(profile, aggregates, periodStart, periodEnd);

        // Step 6: build summary text
        String summary = buildSummary(profile, aggregates, provenanceLines, periodStart);

        // Step 7: package as ZIP
        byte[] zipBytes = buildZip(xmlBytes, summary.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                periodStart, tenantId);

        String filename = "okir-kg-kgyf-ne-" + periodStart.getYear()
                + "-Q" + KgKgyfNeMarshaller.quarterOf(periodStart) + ".zip";

        return new EprReportArtifact(
                filename,
                "application/zip",
                zipBytes,
                xmlBytes,
                summary,
                provenanceLines
        );
    }

    // ─── Invoice line processing ──────────────────────────────────────────────

    private void processLineItem(UUID tenantId, String invoiceNumber, InvoiceLineItem item,
                                  List<RegistryWeightContribution> contributions,
                                  List<EprReportProvenance> provenanceLines) {
        // Skip lines with missing VTSZ or non-positive quantity
        if (item.vtszCode() == null || item.vtszCode().isBlank()) return;
        if (item.quantity() == null || item.quantity().compareTo(BigDecimal.ZERO) <= 0) return;

        String vtszCode = item.vtszCode();
        String productName = item.lineDescription() != null ? item.lineDescription() : "";
        BigDecimal quantity = item.quantity();

        // Article number may be in productCodeValue when productCodeCategory indicates OWN
        String articleNumber = "OWN".equalsIgnoreCase(item.productCodeCategory())
                ? item.productCodeValue() : null;

        // Registry lookup
        Optional<RegistryMatch> matchOpt =
                registryLookupService.findByVtszOrArticleNumber(tenantId, vtszCode, articleNumber);

        if (matchOpt.isPresent()) {
            RegistryMatch match = matchOpt.get();
            for (ProductPackagingComponent component : match.components()) {
                if (component.kfCode() == null || component.kfCode().isBlank()) continue;
                BigDecimal weightKg = component.weightPerUnitKg().multiply(quantity)
                        .divide(component.itemsPerParent(), 6, java.math.RoundingMode.HALF_UP);
                contributions.add(new RegistryWeightContribution(component.kfCode(), weightKg));
                provenanceLines.add(new EprReportProvenance(
                        invoiceNumber, item.lineNumber(), vtszCode, productName, quantity,
                        item.unitOfMeasure(), ProvenanceTag.REGISTRY_MATCH,
                        component.kfCode(), weightKg, match.productId()
                ));
            }
            if (match.components().isEmpty()) {
                // Product found but no components → treat as UNMATCHED
                provenanceLines.add(unmatchedProvenance(invoiceNumber, item, vtszCode, productName, quantity));
            }
        } else {
            // Registry miss: try classifier for VTSZ_FALLBACK annotation only
            var classResult = classifierService.classify(productName, vtszCode);
            if (!classResult.suggestions().isEmpty()
                    && classResult.strategy() == ClassificationStrategy.VTSZ_PREFIX) {
                String suggestedKf = classResult.suggestions().get(0).kfCode();
                provenanceLines.add(new EprReportProvenance(
                        invoiceNumber, item.lineNumber(), vtszCode, productName, quantity,
                        item.unitOfMeasure(), ProvenanceTag.VTSZ_FALLBACK,
                        suggestedKf, null, null
                ));
            } else {
                provenanceLines.add(unmatchedProvenance(invoiceNumber, item, vtszCode, productName, quantity));
            }
        }
    }

    private static EprReportProvenance unmatchedProvenance(String invoiceNumber, InvoiceLineItem item,
                                                            String vtszCode, String productName,
                                                            BigDecimal quantity) {
        return new EprReportProvenance(
                invoiceNumber, item.lineNumber(), vtszCode, productName, quantity,
                item.unitOfMeasure(), ProvenanceTag.UNMATCHED, null, null, null
        );
    }

    // ─── ZIP packaging ────────────────────────────────────────────────────────

    private static byte[] buildZip(byte[] xmlBytes, byte[] summaryBytes,
                                    LocalDate periodStart, UUID tenantId) {
        int year = periodStart.getYear();
        int quarter = KgKgyfNeMarshaller.quarterOf(periodStart);
        String tenantShortId = tenantId.toString().replace("-", "").substring(0, 8);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bos)) {
            zip.putNextEntry(new ZipEntry("KG-KGYF-NE-" + year + "-Q" + quarter + "-" + tenantShortId + ".xml"));
            zip.write(xmlBytes);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("summary-" + year + "-Q" + quarter + "-" + tenantShortId + ".txt"));
            zip.write(summaryBytes);
            zip.closeEntry();
            zip.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create OKIRkapu ZIP package", e);
        }
    }

    // ─── Summary text ─────────────────────────────────────────────────────────

    private static String buildSummary(ProducerProfile profile, List<KfCodeAggregate> aggregates,
                                        List<EprReportProvenance> provenanceLines, LocalDate periodStart) {
        int year = periodStart.getYear();
        int quarter = KgKgyfNeMarshaller.quarterOf(periodStart);
        long matched = provenanceLines.stream().filter(p -> p.tag() == ProvenanceTag.REGISTRY_MATCH).count();
        long fallback = provenanceLines.stream().filter(p -> p.tag() == ProvenanceTag.VTSZ_FALLBACK).count();
        long unmatched = provenanceLines.stream().filter(p -> p.tag() == ProvenanceTag.UNMATCHED).count();

        StringBuilder sb = new StringBuilder();
        sb.append("OKIR KG:KGYF-NÉ Adatszolgáltatás Összesítő\n");
        sb.append("===========================================\n");
        sb.append("Cég: ").append(profile.legalName()).append('\n');
        sb.append("Adószám: ").append(profile.taxNumber() != null ? profile.taxNumber() : "—").append('\n');
        sb.append("Időszak: ").append(year).append(" Q").append(quarter).append('\n');
        sb.append("Generálva: ").append(Instant.now()).append('\n');
        sb.append("XSD verzió: ").append(KgKgyfNeMarshaller.XSD_VERSION).append('\n');
        sb.append('\n');
        sb.append("Nyilvántartás összesítés:\n");
        sb.append("  Illeszkedett sorok (XML-be kerül): ").append(matched).append('\n');
        sb.append("  VTSZ visszaesési sorok (XML-ből kizárva): ").append(fallback).append('\n');
        sb.append("  Nem illeszkedett sorok: ").append(unmatched).append('\n');
        if (fallback > 0) {
            sb.append("  ⚠ VTSZ visszaesési sorok megjelennek az összesítőben az Ön tájékoztatására, de NEM kerülnek\n");
            sb.append("    be az XML-be, mert nincs összetevőszintű súlyadat. Regisztrálja ezeket a termékeket\n");
            sb.append("    a pontos bevallás érdekében.\n");
        }
        sb.append('\n');
        sb.append("KF kód összesítés:\n");
        if (aggregates.isEmpty()) {
            sb.append("  (Nulla bejelentés — nincs nyilvántartott termék a megadott időszakban)\n");
        } else {
            for (KfCodeAggregate agg : aggregates) {
                sb.append(String.format("  %s: %.3f kg (%d sor)%n",
                        agg.kfCode(), agg.totalWeightKg(), agg.lineCount()));
            }
        }
        sb.append('\n');
        sb.append("Számlasor részletezés:\n");
        sb.append(String.format("%-20s %5s %-10s %-40s %10s %5s %-12s %-8s %12s%n",
                "Számlaszám", "Sor", "VTSZ", "Termék", "Mennyiség", "Egys.", "Minősítés", "KF kód", "Súly (kg)"));
        sb.append("-".repeat(130)).append('\n');
        for (EprReportProvenance p : provenanceLines) {
            sb.append(String.format("%-20s %5d %-10s %-40s %10s %5s %-12s %-8s %12s%n",
                    p.invoiceNumber(),
                    p.lineNumber(),
                    p.vtszCode() != null ? p.vtszCode() : "—",
                    truncate(p.productName(), 40),
                    p.quantity() != null ? p.quantity().toPlainString() : "—",
                    p.unitOfMeasure() != null ? p.unitOfMeasure() : "—",
                    p.tag().name(),
                    p.resolvedKfCode() != null ? p.resolvedKfCode() : "—",
                    p.aggregatedWeightKg() != null ? p.aggregatedWeightKg().toPlainString() : "—"
            ));
        }
        sb.append('\n');
        List<EprReportProvenance> unmatchedLines = provenanceLines.stream()
                .filter(p -> p.tag() == ProvenanceTag.UNMATCHED).toList();
        if (!unmatchedLines.isEmpty()) {
            sb.append("Nem illeszkedett számlasorok:\n");
            sb.append("A következő számlasorok nincsenek a nyilvántartásban — regisztrálja őket a pontosabb bevallásért:\n");
            for (EprReportProvenance p : unmatchedLines) {
                sb.append("  - ").append(p.invoiceNumber()).append('/').append(p.lineNumber())
                        .append(": ").append(p.productName()).append(" (VTSZ: ").append(p.vtszCode()).append(")\n");
            }
            sb.append('\n');
        }
        sb.append("Bejelentkezési útmutató:\n");
        sb.append("1. Jelentkezzen be a kapu.okir.hu-ra\n");
        sb.append("2. Új adatcsomag XML-ből → töltse fel a csatolt XML-t.\n");
        sb.append("Az Országos Hulladékgazdálkodási Hatóság a 25-ig továbbítja a MOHU felé.\n");
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        return s == null ? "—" : s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
