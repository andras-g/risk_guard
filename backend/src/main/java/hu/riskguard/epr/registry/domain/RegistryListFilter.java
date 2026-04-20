package hu.riskguard.epr.registry.domain;

import hu.riskguard.epr.audit.AuditSource;

/**
 * Filter parameters for listing products.
 * All fields are optional (null = no filter on that dimension).
 *
 * <p>Story 10.4 adds {@code reviewState} and {@code classifierSource} for the
 * "Csak hiányos" / "Csak bizonytalan" toolbar chips (AC #24).
 */
public record RegistryListFilter(
        String q,                       // free-text substring on name (ILIKE)
        String vtsz,                    // prefix match on vtsz
        String kfCode,                  // exact match via components join
        ProductStatus status,
        ReviewState reviewState,         // null = all; MISSING_PACKAGING = only incomplete
        AuditSource classifierSource     // null = all; VTSZ_FALLBACK = only uncertain
) {
    /** Backward-compatible 4-field constructor for existing callers. */
    public RegistryListFilter(String q, String vtsz, String kfCode, ProductStatus status) {
        this(q, vtsz, kfCode, status, null, null);
    }
}
