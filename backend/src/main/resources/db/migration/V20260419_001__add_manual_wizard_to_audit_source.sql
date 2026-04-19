-- Story 10.2: AuditSource.MANUAL_WIZARD — distinguishes wizard-driven Registry KF-code
-- resolutions from hand-typed MANUAL entries. See ADR-0003 + Story 10.2 AC #1-3.
--
-- Drop the auto-named CHECK from V20260414_001__create_product_registry.sql and
-- re-add it with the new value included. IF EXISTS guards against a partial prior
-- migration state so re-running this file against a half-migrated DB is safe.

ALTER TABLE registry_entry_audit_log
    DROP CONSTRAINT IF EXISTS registry_entry_audit_log_source_check;

ALTER TABLE registry_entry_audit_log
    ADD CONSTRAINT registry_entry_audit_log_source_check
    CHECK (source IN (
        'MANUAL',
        'AI_SUGGESTED_CONFIRMED',
        'AI_SUGGESTED_EDITED',
        'VTSZ_FALLBACK',
        'NAV_BOOTSTRAP',
        'MANUAL_WIZARD'
    ));
