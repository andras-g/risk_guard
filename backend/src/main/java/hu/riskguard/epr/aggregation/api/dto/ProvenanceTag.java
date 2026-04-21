package hu.riskguard.epr.aggregation.api.dto;

/**
 * How a provenance line was resolved during aggregation (Story 10.8).
 *
 * <p>Distinct from {@code epr.report.ProvenanceTag} (3-value enum used by the OKIRkapu
 * XML exporter). Do NOT merge these two enums — they serve different concerns.
 */
public enum ProvenanceTag {
    /** Invoice line matched a registry product via exact VTSZ + description lookup. */
    REGISTRY_MATCH,
    /** Invoice line matched via VTSZ-prefix fallback; requires registry review. */
    VTSZ_FALLBACK,
    /** No registry product matched this invoice line. */
    UNRESOLVED,
    /** Unit of measure is not supported (only DARAB is accepted); weight = 0. */
    UNSUPPORTED_UNIT
}
