package hu.riskguard.epr.registry.api.dto;

import hu.riskguard.epr.registry.internal.RegistryRepository.RegistrySummary;

public record RegistrySummaryResponse(int totalProducts, int productsWithComponents) {
    public static RegistrySummaryResponse from(RegistrySummary summary) {
        return new RegistrySummaryResponse(summary.totalProducts(), summary.productsWithComponents());
    }
}
