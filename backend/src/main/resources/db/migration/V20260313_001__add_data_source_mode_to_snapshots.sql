-- Story 2.2.2: Add data_source_mode column to company_snapshots
-- Tracks which data source mode (demo/test/live) produced each snapshot.
-- Default 'demo' for existing rows since all prior data came from fictional adapters.

ALTER TABLE company_snapshots
    ADD COLUMN IF NOT EXISTS data_source_mode VARCHAR(10) DEFAULT 'demo';

-- Backfill existing rows
UPDATE company_snapshots SET data_source_mode = 'demo' WHERE data_source_mode IS NULL;
