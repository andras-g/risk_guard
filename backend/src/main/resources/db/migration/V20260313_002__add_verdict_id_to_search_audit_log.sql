-- Story 2.5: SHA-256 Audit Logging (Legal Proof)
-- Add verdict_id FK to search_audit_log for full legal proof traceability.
-- Column is nullable (ON DELETE SET NULL) so existing rows without a verdict_id remain valid.

ALTER TABLE search_audit_log
    ADD COLUMN IF NOT EXISTS verdict_id UUID REFERENCES verdicts(id) ON DELETE SET NULL;

-- Index for verdict → audit log lookups (admin audit viewer, Story 6.4)
CREATE INDEX IF NOT EXISTS idx_search_audit_log_verdict ON search_audit_log (verdict_id);
