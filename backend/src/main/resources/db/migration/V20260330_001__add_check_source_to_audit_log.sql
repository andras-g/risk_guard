-- Story 5.1a: Add check_source and data_source_mode columns to search_audit_log
-- Backfills existing rows via column defaults (MANUAL / DEMO).

ALTER TABLE search_audit_log
    ADD COLUMN check_source    VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN data_source_mode VARCHAR(10) NOT NULL DEFAULT 'DEMO';

-- Composite index for the filtered, sorted audit history query (tenant + tax number + timestamp DESC)
CREATE INDEX idx_audit_tenant_tax_searched
    ON search_audit_log (tenant_id, tax_number, searched_at DESC);
