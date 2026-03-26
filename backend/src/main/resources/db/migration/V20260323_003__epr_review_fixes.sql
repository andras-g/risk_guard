-- Code review R1 fixes for EPR tables
-- Story 4.0: EPR Module Foundation — Review Follow-ups
--
-- H2: epr_configs.version must be UNIQUE — prevents duplicate version records
--     that would break fee lookups (only one config per version number).
-- H3: epr_calculations.kf_code and fee_rate should be nullable to support
--     partial-save during the DAG wizard flow (Story 4.2).

-- H2: Add UNIQUE constraint on epr_configs.version
-- Drop the non-unique index first, then recreate as unique
DROP INDEX IF EXISTS idx_epr_configs_version;
CREATE UNIQUE INDEX idx_epr_configs_version ON epr_configs(version);

-- H3: Make kf_code and fee_rate nullable for partial-save support
ALTER TABLE epr_calculations ALTER COLUMN kf_code DROP NOT NULL;
ALTER TABLE epr_calculations ALTER COLUMN fee_rate DROP NOT NULL;
