-- Story 2.2: Parallel Scraper Engine & Outage Resilience
-- Adds scraping-related columns to company_snapshots table

-- Source URLs — JSONB map of adapter → URL pairs used for provenance tracking
ALTER TABLE company_snapshots ADD COLUMN source_urls JSONB NOT NULL DEFAULT '{}';

-- DOM fingerprint hash — SHA-256 of concatenated HTML DOMs for change detection
ALTER TABLE company_snapshots ADD COLUMN dom_fingerprint_hash VARCHAR(64);

-- Checked-at — timestamp of when scraping was last performed (distinct from created_at)
ALTER TABLE company_snapshots ADD COLUMN checked_at TIMESTAMPTZ;

-- Backfill checked_at from created_at for existing rows
UPDATE company_snapshots SET checked_at = created_at WHERE checked_at IS NULL;
