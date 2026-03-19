-- Backfill company_name on watchlist_entries from company_snapshots.snapshot_data JSONB.
-- Story 3.7 fix: company_name was not populated at insert time (passed as null from
-- WatchlistController). The old lateral join masked this by extracting the name at query
-- time. Now that we read the column directly, we need the data to be present.
--
-- Iterates JSONB top-level keys (adapter names vary: "demo", "nav-debt", etc.) and
-- extracts the first non-null "companyName" value found in any adapter section.
-- Reads from screening-owned tables (company_snapshots) — acceptable in migration context only.

UPDATE watchlist_entries we
SET company_name = sub.name
FROM (
    SELECT DISTINCT ON (cs.tenant_id, cs.tax_number)
        cs.tenant_id,
        cs.tax_number,
        (
            SELECT value ->> 'companyName'
            FROM jsonb_each(cs.snapshot_data)
            WHERE value ->> 'companyName' IS NOT NULL
            LIMIT 1
        ) AS name
    FROM company_snapshots cs
    ORDER BY cs.tenant_id, cs.tax_number, cs.created_at DESC
) sub
WHERE we.tenant_id = sub.tenant_id
  AND we.tax_number = sub.tax_number
  AND sub.name IS NOT NULL
  AND (we.company_name IS NULL OR we.company_name = '');
