package hu.riskguard.epr.aggregation.api;

/**
 * Reason why an invoice line could not be fully resolved against the Registry.
 * VTSZ_FALLBACK lines still contribute to kfTotals — they appear in unresolved as a warning only.
 */
public enum UnresolvedReason {
    NO_MATCHING_PRODUCT,
    UNSUPPORTED_UNIT_OF_MEASURE,
    ZERO_COMPONENTS,
    VTSZ_FALLBACK
}
