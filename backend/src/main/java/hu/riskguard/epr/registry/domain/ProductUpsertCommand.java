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
        List<ComponentUpsertCommand> components
) {}
