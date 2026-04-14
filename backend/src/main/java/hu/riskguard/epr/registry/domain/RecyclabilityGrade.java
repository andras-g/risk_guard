package hu.riskguard.epr.registry.domain;

/**
 * PPWR (Regulation 2025/40) recyclability grade for a packaging component.
 *
 * <p>Placed in {@code epr.registry.domain} deliberately — the ArchUnit rule
 * {@code fee_calculation_must_not_branch_on_recyclability_grade} forbids importing
 * this enum from {@code epr.domain} or {@code epr.report} packages so that fee
 * modulation remains data-driven (from the EPR config/DB), not code-branched.
 */
public enum RecyclabilityGrade {
    A, B, C, D
}
