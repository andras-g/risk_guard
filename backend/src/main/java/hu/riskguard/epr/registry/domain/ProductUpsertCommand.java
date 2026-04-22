package hu.riskguard.epr.registry.domain;

import java.util.List;

/**
 * Command object for creating or updating a product and its packaging components.
 */
public record ProductUpsertCommand(
        String articleNumber,
        String name,
        String vtsz,
        String primaryUnit,
        ProductStatus status,
        String eprScope,
        List<ComponentUpsertCommand> components
) {
    /** Convenience overload for callers that do not yet know about epr_scope. */
    public ProductUpsertCommand(String articleNumber, String name, String vtsz,
                                String primaryUnit, ProductStatus status,
                                List<ComponentUpsertCommand> components) {
        this(articleNumber, name, vtsz, primaryUnit, status, null, components);
    }
}
