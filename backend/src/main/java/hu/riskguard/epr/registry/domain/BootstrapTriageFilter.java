package hu.riskguard.epr.registry.domain;

/**
 * Filter for the triage candidate list query.
 *
 * @param status if {@code null}, all statuses are returned
 */
public record BootstrapTriageFilter(BootstrapCandidateStatus status) {}
