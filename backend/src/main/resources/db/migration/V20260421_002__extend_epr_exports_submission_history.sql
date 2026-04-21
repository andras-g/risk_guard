-- Story 10.9: Extend epr_exports with submission history columns
-- Idempotent: all ADD COLUMN IF NOT EXISTS; index uses IF NOT EXISTS; DELETE clears dev-only data.

ALTER TABLE epr_exports ADD COLUMN IF NOT EXISTS total_fee_huf    DECIMAL(18,2);
ALTER TABLE epr_exports ADD COLUMN IF NOT EXISTS total_weight_kg  DECIMAL(18,3);
ALTER TABLE epr_exports ADD COLUMN IF NOT EXISTS xml_content      BYTEA;
ALTER TABLE epr_exports ADD COLUMN IF NOT EXISTS submitted_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE epr_exports ADD COLUMN IF NOT EXISTS file_name        VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_epr_exports_tenant_period
    ON epr_exports(tenant_id, period_end DESC);

-- Delete pre-10.9 rows (dev-only data; demo seed is refreshed in Task 15).
DELETE FROM epr_exports;

-- Rollback:
-- ALTER TABLE epr_exports DROP COLUMN IF EXISTS total_fee_huf;
-- ALTER TABLE epr_exports DROP COLUMN IF EXISTS total_weight_kg;
-- ALTER TABLE epr_exports DROP COLUMN IF EXISTS xml_content;
-- ALTER TABLE epr_exports DROP COLUMN IF EXISTS submitted_by_user_id;
-- ALTER TABLE epr_exports DROP COLUMN IF EXISTS file_name;
-- DROP INDEX IF EXISTS idx_epr_exports_tenant_period;
