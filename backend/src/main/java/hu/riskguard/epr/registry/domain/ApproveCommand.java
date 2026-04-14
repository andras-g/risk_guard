package hu.riskguard.epr.registry.domain;

import java.util.List;

/**
 * Command for approving a bootstrap candidate and promoting it to a registry product.
 * Carries the user-edited product data from the approve dialog.
 */
public record ApproveCommand(
        String articleNumber,
        String name,
        String vtsz,
        String primaryUnit,
        ProductStatus status,
        List<ComponentUpsertCommand> components
) {

    /** Convert to the upsert command accepted by {@link RegistryService#create}. */
    public ProductUpsertCommand toProductUpsertCommand() {
        return new ProductUpsertCommand(articleNumber, name, vtsz, primaryUnit, status, components);
    }
}
