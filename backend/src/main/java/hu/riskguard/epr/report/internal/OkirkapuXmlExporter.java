package hu.riskguard.epr.report.internal;

import hu.riskguard.epr.aggregation.api.dto.KfCodeTotal;
import hu.riskguard.epr.producer.domain.ProducerProfile;
import hu.riskguard.epr.report.EprReportArtifact;
import hu.riskguard.epr.report.EprReportTarget;
import hu.riskguard.epr.report.internal.KgKgyfNeAggregator.KfCodeAggregate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * OKIRkapu-specific implementation of {@link EprReportTarget}.
 *
 * <p>Activated when {@code riskguard.epr.report.target=okirkapu-xml} (the default, per ADR-0002).
 *
 * <p>This is a pure marshalling concern: it receives pre-computed {@link KfCodeTotal} records
 * from {@code InvoiceDrivenFilingAggregator} and maps them to the XML structure.
 * Invoice walking and registry lookup happen upstream; this class only marshals.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "riskguard.epr.report.target", havingValue = "okirkapu-xml", matchIfMissing = true)
@RequiredArgsConstructor
public class OkirkapuXmlExporter implements EprReportTarget {

    private final KgKgyfNeMarshaller marshaller;

    @Override
    public EprReportArtifact generate(List<KfCodeTotal> kfTotals, ProducerProfile producerProfile,
                                       LocalDate periodStart, LocalDate periodEnd) {
        List<KfCodeAggregate> aggregates = kfTotals.stream()
                .map(t -> new KfCodeAggregate(t.kfCode(), t.totalWeightKg(), t.contributingProductCount()))
                .toList();

        byte[] xmlBytes = marshaller.marshal(producerProfile, aggregates, periodStart, periodEnd);
        String summary = buildSummary(producerProfile, kfTotals, periodStart);
        byte[] zipBytes = buildZip(xmlBytes, summary.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                periodStart, producerProfile.tenantId());

        String filename = "okir-kg-kgyf-ne-" + periodStart.getYear()
                + "-Q" + KgKgyfNeMarshaller.quarterOf(periodStart) + ".zip";

        log.info("OkirkapuXmlExporter: generated report period={}/{} kfCodes={} totalProducts={}",
                periodStart, periodEnd, aggregates.size(),
                kfTotals.stream().mapToInt(KfCodeTotal::contributingProductCount).sum());

        return new EprReportArtifact(filename, "application/zip", zipBytes, xmlBytes, summary, List.of());
    }

    // ─── ZIP packaging ────────────────────────────────────────────────────────

    private static byte[] buildZip(byte[] xmlBytes, byte[] summaryBytes,
                                    LocalDate periodStart, java.util.UUID tenantId) {
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

    private static String buildSummary(ProducerProfile profile, List<KfCodeTotal> kfTotals,
                                        LocalDate periodStart) {
        int year = periodStart.getYear();
        int quarter = KgKgyfNeMarshaller.quarterOf(periodStart);
        long fallbackCount = kfTotals.stream().filter(KfCodeTotal::hasFallback).count();
        long overflowCount = kfTotals.stream().filter(KfCodeTotal::hasOverflowWarning).count();

        StringBuilder sb = new StringBuilder();
        sb.append("OKIR KG:KGYF-NÉ Adatszolgáltatás Összesítő\n");
        sb.append("===========================================\n");
        sb.append("Cég: ").append(profile.legalName()).append('\n');
        sb.append("Adószám: ").append(profile.taxNumber() != null ? profile.taxNumber() : "—").append('\n');
        sb.append("Időszak: ").append(year).append(" Q").append(quarter).append('\n');
        sb.append("Generálva: ").append(Instant.now()).append('\n');
        sb.append("XSD verzió: ").append(KgKgyfNeMarshaller.XSD_VERSION).append('\n');
        sb.append('\n');
        sb.append("KF kód összesítés:\n");
        if (kfTotals.isEmpty()) {
            sb.append("  (Nulla bejelentés — nincs nyilvántartott termék a megadott időszakban)\n");
        } else {
            for (KfCodeTotal t : kfTotals) {
                sb.append(String.format(Locale.ROOT,
                        "  %s (%s): %.3f kg × %.2f Ft/kg = %.0f Ft (%d termék)%n",
                        t.kfCode(), t.classificationLabel(),
                        t.totalWeightKg(), t.feeRateHufPerKg(), t.totalFeeHuf(),
                        t.contributingProductCount()));
                if (t.hasFallback()) {
                    sb.append("    ⚠ Tartalmaz VTSZ visszaesési sorokat\n");
                }
                if (t.hasOverflowWarning()) {
                    sb.append("    ⚠ Rendkívüli súlyérték (>100M kg) — ellenőrizze az adatokat!\n");
                }
            }
        }
        if (fallbackCount > 0 || overflowCount > 0) {
            sb.append('\n');
            sb.append("Figyelmeztetések:\n");
            if (fallbackCount > 0) {
                sb.append("  ⚠ ").append(fallbackCount).append(" KF kód tartalmaz VTSZ visszaesési sorokat.\n");
                sb.append("    Regisztrálja a termékeket a pontos bevallás érdekében.\n");
            }
            if (overflowCount > 0) {
                sb.append("  ⚠ ").append(overflowCount).append(" KF kód rendkívüli súlyértékkel rendelkezik.\n");
            }
        }
        sb.append('\n');
        sb.append("Bejelentkezési útmutató:\n");
        sb.append("1. Jelentkezzen be a kapu.okir.hu-ra\n");
        sb.append("2. Új adatcsomag XML-ből → töltse fel a csatolt XML-t.\n");
        sb.append("Az Országos Hulladékgazdálkodási Hatóság a 25-ig továbbítja a MOHU felé.\n");
        return sb.toString();
    }
}
