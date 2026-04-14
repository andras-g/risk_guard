-- Story 9.2: NAV-Invoice-Driven Registry Bootstrap
-- Creates the registry_bootstrap_candidates table used to stage NAV invoice
-- line items for human triage before they are promoted to registry products.

CREATE TABLE registry_bootstrap_candidates (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL REFERENCES tenants(id),
    product_name            VARCHAR(512)    NOT NULL,
    vtsz                    VARCHAR(16)     NULL,
    frequency               INT             NOT NULL DEFAULT 1,
    total_quantity          NUMERIC(14,3)   NOT NULL DEFAULT 0,
    unit_of_measure         VARCHAR(16)     NULL,
    status                  VARCHAR(48)     NOT NULL DEFAULT 'PENDING'
                                            CHECK (status IN (
                                                'PENDING',
                                                'APPROVED',
                                                'REJECTED_NOT_OWN_PACKAGING',
                                                'NEEDS_MANUAL_ENTRY'
                                            )),
    suggested_kf_code       VARCHAR(16)     NULL,
    suggested_components    JSONB           NULL,
    classification_strategy VARCHAR(32)     NULL,
    classification_confidence VARCHAR(16)   NULL,
    resulting_product_id    UUID            NULL REFERENCES products(id) ON DELETE SET NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Composite indexes for common query patterns
CREATE INDEX idx_rbc_tenant_status       ON registry_bootstrap_candidates (tenant_id, status);
CREATE INDEX idx_rbc_tenant_product_name ON registry_bootstrap_candidates (tenant_id, product_name);

-- Dedup contract: one candidate per (tenant, product_name, vtsz) — prevents duplicate triage entries.
-- NULL vtsz values are treated as distinct by default in Postgres partial indexes,
-- but we include all rows (including vtsz IS NULL) in a single unique index using COALESCE.
CREATE UNIQUE INDEX uq_rbc_tenant_product_vtsz
    ON registry_bootstrap_candidates (tenant_id, product_name, COALESCE(vtsz, ''));

-- ─── updated_at auto-maintenance ───────────────────────────────────────────────
-- Reuse the set_updated_at() function already created by V20260414_001.
-- CREATE OR REPLACE is safe to call even though the function already exists.

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_rbc_updated_at
    BEFORE UPDATE ON registry_bootstrap_candidates
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
