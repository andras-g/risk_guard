package hu.riskguard.epr.report;

import hu.riskguard.epr.aggregation.api.dto.KfCodeTotal;
import hu.riskguard.epr.producer.domain.ProducerProfile;

import java.time.LocalDate;
import java.util.List;

/**
 * Strategy interface for EPR report generation (ADR-0002).
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@code OkirkapuXmlExporter} — default, activated by {@code riskguard.epr.report.target=okirkapu-xml}</li>
 *   <li>Future: {@code EuRegistryAdapter} for post-2029 EU-wide registry submission</li>
 * </ul>
 *
 * <p>Callers ({@code EprService}) depend on this interface only. ArchUnit rule
 * {@code only_report_package_depends_on_concrete_report_target} enforces this boundary.
 */
public interface EprReportTarget {

    /**
     * Generate a complete EPR report artifact from pre-computed KF-code totals.
     * The exporter is a pure marshalling concern — it does NOT walk invoices.
     *
     * @param kfTotals      pre-computed aggregated totals per KF code (from InvoiceDrivenFilingAggregator)
     * @param producerProfile fully populated producer profile
     * @param periodStart   start of the reporting period
     * @param periodEnd     end of the reporting period
     * @return the generated artifact including file bytes, summary, and provenance lines
     */
    EprReportArtifact generate(List<KfCodeTotal> kfTotals, ProducerProfile producerProfile,
                                LocalDate periodStart, LocalDate periodEnd);
}
