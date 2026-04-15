package hu.riskguard.epr.api.dto;

import hu.riskguard.epr.report.EprReportArtifact;

import java.util.List;

/**
 * Response DTO for the OKIRkapu preview endpoint.
 * Returns provenance lines and summary text without the binary file.
 */
public record OkirkapuPreviewResponse(
        List<EprReportProvenanceDto> provenanceLines,
        String summary
) {

    public static OkirkapuPreviewResponse from(EprReportArtifact artifact) {
        List<EprReportProvenanceDto> lines = artifact.provenanceLines().stream()
                .map(EprReportProvenanceDto::from)
                .toList();
        return new OkirkapuPreviewResponse(lines, artifact.summaryReport());
    }
}
