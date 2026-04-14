package hu.riskguard.epr.registry.domain;

/**
 * Lifecycle status of a {@link BootstrapCandidate}.
 * Matches the DB check constraint defined in the Flyway migration.
 */
public enum BootstrapCandidateStatus {
    PENDING,
    APPROVED,
    REJECTED_NOT_OWN_PACKAGING,
    NEEDS_MANUAL_ENTRY
}
