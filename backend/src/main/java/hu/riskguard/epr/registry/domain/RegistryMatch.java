package hu.riskguard.epr.registry.domain;

import java.util.List;
import java.util.UUID;

/**
 * Result of a registry lookup: the matched product ID and its packaging components.
 */
public record RegistryMatch(
        UUID productId,
        List<ProductPackagingComponent> components
) {}
