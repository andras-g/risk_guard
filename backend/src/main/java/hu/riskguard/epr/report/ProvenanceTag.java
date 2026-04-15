package hu.riskguard.epr.report;

/**
 * Classification of how an invoice line item was resolved during report generation.
 *
 * <ul>
 *   <li>{@code REGISTRY_MATCH} — matched a product in the tenant's product-packaging registry;
 *       contributes to XML aggregation (green in preview)</li>
 *   <li>{@code VTSZ_FALLBACK} — matched via VTSZ-prefix classifier only;
 *       does NOT contribute to XML (amber in preview)</li>
 *   <li>{@code UNMATCHED} — no match found; excluded from XML (red in preview)</li>
 * </ul>
 */
public enum ProvenanceTag {
    REGISTRY_MATCH,
    VTSZ_FALLBACK,
    UNMATCHED
}
