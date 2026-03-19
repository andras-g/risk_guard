-- Notification Module: Add denormalized verdict columns to watchlist_entries
-- Story 3.7: 24h Background Monitoring Cycle
-- Resolves acknowledged tech debt from Story 3.6 (lateral join on verdicts/company_snapshots).
-- These columns are updated by:
--   1. WatchlistMonitor (@Scheduled 24h cycle) — directly after re-evaluation
--   2. PartnerStatusChangedListener — reactively on any PartnerStatusChanged event

ALTER TABLE watchlist_entries
    ADD COLUMN last_verdict_status VARCHAR(20),
    ADD COLUMN last_checked_at TIMESTAMPTZ;

-- Backfill from latest verdicts (one-time migration data fill).
-- Uses DISTINCT ON to get the most recent verdict per tenant+tax_number combination.
-- Reads from screening-owned tables (verdicts, company_snapshots) — acceptable in migration context only.
UPDATE watchlist_entries we
SET last_verdict_status = sub.status,
    last_checked_at = sub.created_at
FROM (
    SELECT DISTINCT ON (cs.tenant_id, cs.tax_number)
        cs.tenant_id, cs.tax_number, v.status::text, v.created_at
    FROM verdicts v
    JOIN company_snapshots cs ON v.snapshot_id = cs.id
    ORDER BY cs.tenant_id, cs.tax_number, v.created_at DESC
) sub
WHERE we.tenant_id = sub.tenant_id
  AND we.tax_number = sub.tax_number;
