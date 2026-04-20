package hu.riskguard.epr.registry.bootstrap.domain;

/**
 * Status machine for epr_bootstrap_jobs rows.
 * Transitions: PENDING → RUNNING → COMPLETED | FAILED_PARTIAL | FAILED | CANCELLED
 */
public enum BootstrapJobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    FAILED_PARTIAL,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == FAILED_PARTIAL || this == CANCELLED;
    }
}
