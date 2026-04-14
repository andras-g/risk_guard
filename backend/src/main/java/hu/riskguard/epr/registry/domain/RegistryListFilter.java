package hu.riskguard.epr.registry.domain;

/**
 * Filter parameters for listing products.
 * All fields are optional (null = no filter on that dimension).
 */
public record RegistryListFilter(
        String q,           // free-text substring on name (ILIKE)
        String vtsz,        // prefix match on vtsz
        String kfCode,      // exact match via components join
        ProductStatus status
) {}
