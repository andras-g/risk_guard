-- Screening Module Tables
-- Story 2.1: Tax Number Search & Skeleton UI

-- ENUM types for verdict status and confidence
CREATE TYPE verdict_status AS ENUM (
    'RELIABLE',
    'AT_RISK',
    'INCOMPLETE',
    'TAX_SUSPENDED',
    'UNAVAILABLE'
);

CREATE TYPE verdict_confidence AS ENUM (
    'FRESH',
    'STALE',
    'UNAVAILABLE'
);

-- Company snapshots — stores raw scraping results per tax number per tenant
CREATE TABLE company_snapshots (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    tax_number VARCHAR(11) NOT NULL,
    snapshot_data JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Verdicts — deterministic risk assessment linked to a snapshot
CREATE TABLE verdicts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    snapshot_id UUID NOT NULL REFERENCES company_snapshots(id),
    status verdict_status NOT NULL DEFAULT 'INCOMPLETE',
    confidence verdict_confidence NOT NULL DEFAULT 'UNAVAILABLE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Search audit log — legal proof trail with SHA-256 hash
CREATE TABLE search_audit_log (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    tax_number VARCHAR(11) NOT NULL,
    searched_by UUID NOT NULL REFERENCES users(id),
    sha256_hash VARCHAR(64) NOT NULL,
    disclaimer_text TEXT NOT NULL,
    searched_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes: BRIN on timestamp columns for time-range scans
CREATE INDEX idx_company_snapshots_created_at ON company_snapshots USING BRIN (created_at);
CREATE INDEX idx_verdicts_created_at ON verdicts USING BRIN (created_at);
CREATE INDEX idx_search_audit_log_searched_at ON search_audit_log USING BRIN (searched_at);

-- Indexes: B-tree on tenant_id + tax_number composites for tenant-scoped lookups
CREATE INDEX idx_company_snapshots_tenant_tax ON company_snapshots(tenant_id, tax_number);
CREATE INDEX idx_search_audit_log_tenant_tax ON search_audit_log(tenant_id, tax_number);

-- Index: B-tree on verdict -> snapshot for join queries
CREATE INDEX idx_verdicts_snapshot ON verdicts(snapshot_id);
CREATE INDEX idx_verdicts_tenant ON verdicts(tenant_id);
