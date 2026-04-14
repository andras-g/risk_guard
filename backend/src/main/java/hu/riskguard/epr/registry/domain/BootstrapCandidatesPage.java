package hu.riskguard.epr.registry.domain;

import java.util.List;

/**
 * Paginated result from {@code RegistryBootstrapService.listCandidates()}.
 */
public record BootstrapCandidatesPage(
        List<BootstrapCandidate> items,
        long total,
        int page,
        int size
) {}
