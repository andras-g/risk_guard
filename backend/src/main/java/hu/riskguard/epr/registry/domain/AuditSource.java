package hu.riskguard.epr.registry.domain;

/**
 * Origin of a change recorded in {@code registry_entry_audit_log}.
 *
 * <p>Only {@code MANUAL} is produced by this story (Story 9.1). The remaining
 * sources are reserved for Stories 9.2 (NAV_BOOTSTRAP) and 9.3 (AI-assisted).
 */
public enum AuditSource {
    MANUAL,
    AI_SUGGESTED_CONFIRMED,
    AI_SUGGESTED_EDITED,
    VTSZ_FALLBACK,
    NAV_BOOTSTRAP
}
