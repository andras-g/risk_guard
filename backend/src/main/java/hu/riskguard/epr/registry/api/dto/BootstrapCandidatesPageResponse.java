package hu.riskguard.epr.registry.api.dto;

import hu.riskguard.epr.registry.domain.BootstrapCandidatesPage;

import java.util.List;

public record BootstrapCandidatesPageResponse(
        List<BootstrapCandidateResponse> items,
        long total,
        int page,
        int size
) {

    public static BootstrapCandidatesPageResponse from(BootstrapCandidatesPage page) {
        return new BootstrapCandidatesPageResponse(
                page.items().stream().map(BootstrapCandidateResponse::from).toList(),
                page.total(),
                page.page(),
                page.size()
        );
    }
}
