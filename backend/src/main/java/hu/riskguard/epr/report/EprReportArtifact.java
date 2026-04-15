package hu.riskguard.epr.report;

import java.util.List;

/**
 * The output of a successful EPR report generation.
 * Contains the raw file bytes, metadata, and line-level provenance for auditability.
 */
public record EprReportArtifact(
        String filename,
        String contentType,
        byte[] bytes,
        byte[] xmlBytes,
        String summaryReport,
        List<EprReportProvenance> provenanceLines
) {}
