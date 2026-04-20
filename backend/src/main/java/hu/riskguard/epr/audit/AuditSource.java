package hu.riskguard.epr.audit;

/**
 * Origin of a change recorded in {@code registry_entry_audit_log}.
 *
 * <p>{@link #MANUAL} is produced by the registry editor's hand-typed KF-code and
 * other direct field edits; {@link #MANUAL_WIZARD} is produced when the user
 * resolves a KF-code via the Registry "Browse" dialog that hosts the 3-step
 * wizard drill-down (Story 10.2); {@link #NAV_BOOTSTRAP} by
 * {@code RegistryBootstrapService}; the AI values by {@code KfCodeClassifierService}.
 * {@link #UNKNOWN} is a forward-compatibility bucket: it is never written — the read
 * path maps any unrecognised DB value to {@code UNKNOWN} so a future removal of an
 * enum constant cannot break audit-log pagination for rows inserted under the old name.
 */
public enum AuditSource {
    MANUAL,
    AI_SUGGESTED_CONFIRMED,
    AI_SUGGESTED_EDITED,
    VTSZ_FALLBACK,
    NAV_BOOTSTRAP,
    MANUAL_WIZARD,
    EPR_AGGREGATION,
    UNKNOWN
}
