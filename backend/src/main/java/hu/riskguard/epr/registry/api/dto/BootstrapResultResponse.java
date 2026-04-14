package hu.riskguard.epr.registry.api.dto;

import hu.riskguard.epr.registry.domain.BootstrapResult;

public record BootstrapResultResponse(int created, int skipped) {

    public static BootstrapResultResponse from(BootstrapResult result) {
        return new BootstrapResultResponse(result.created(), result.skipped());
    }
}
