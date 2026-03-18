-- Notification Module: watchlist_entries table
-- Story 3.5: Async NAV Debt Ingestor (Background Data Freshness)
-- Required by AsyncIngestor to iterate all monitored partners cross-tenant.

CREATE TABLE watchlist_entries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    tax_number VARCHAR(11) NOT NULL,
    label TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, tax_number)
);

-- Index: cross-tenant scan used by AsyncIngestor (no tenant_id filter)
CREATE INDEX idx_watchlist_entries_tax_number ON watchlist_entries(tax_number);

-- Index: tenant-scoped lookups for CRUD operations (Story 3.6)
CREATE INDEX idx_watchlist_entries_tenant ON watchlist_entries(tenant_id);
