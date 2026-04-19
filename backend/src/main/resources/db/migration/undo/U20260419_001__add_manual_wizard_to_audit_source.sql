-- Undo script for V20260419_001__add_manual_wizard_to_audit_source.sql
-- Reverts the CHECK constraint on registry_entry_audit_log.source back to the
-- pre-10.2 five-value set (MANUAL, AI_SUGGESTED_CONFIRMED, AI_SUGGESTED_EDITED,
-- VTSZ_FALLBACK, NAV_BOOTSTRAP).
--
-- Not safe if any registry_entry_audit_log row has source='MANUAL_WIZARD' — the
-- re-added CHECK will reject those rows. Operator responsibility to DELETE or
-- UPDATE those rows first (e.g. to 'MANUAL') before running this undo script.

ALTER TABLE registry_entry_audit_log
    DROP CONSTRAINT IF EXISTS registry_entry_audit_log_source_check;

ALTER TABLE registry_entry_audit_log
    ADD CONSTRAINT registry_entry_audit_log_source_check
    CHECK (source IN (
        'MANUAL',
        'AI_SUGGESTED_CONFIRMED',
        'AI_SUGGESTED_EDITED',
        'VTSZ_FALLBACK',
        'NAV_BOOTSTRAP'
    ));
