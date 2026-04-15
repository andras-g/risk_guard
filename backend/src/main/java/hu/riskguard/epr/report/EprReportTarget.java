package hu.riskguard.epr.report;

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
     * Generate a complete EPR report artifact for the given request.
     *
     * @param request the report parameters (tenant, period, taxNumber)
     * @return the generated artifact including file bytes, summary, and provenance lines
     */
    EprReportArtifact generate(EprReportRequest request);
}
