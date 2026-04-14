-- Story 9.1: Product-Packaging Registry Foundation
-- Creates three tables: products, product_packaging_components, registry_entry_audit_log
-- All tenant-scoped via app-level tenant isolation (no Postgres RLS).
-- CP-5 §4.2 and AC 1 are the authoritative sources.

-- ─── products ──────────────────────────────────────────────────────────────────
CREATE TABLE products (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    article_number  VARCHAR(64)  NULL,
    name            VARCHAR(512) NOT NULL,
    vtsz            VARCHAR(16)  NULL,
    primary_unit    VARCHAR(16)  NOT NULL DEFAULT 'pcs',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                                CHECK (status IN ('ACTIVE','ARCHIVED','DRAFT')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Unique article number per tenant (nulls allowed for draft products)
CREATE UNIQUE INDEX uq_products_tenant_article
    ON products (tenant_id, article_number)
    WHERE article_number IS NOT NULL;

CREATE INDEX idx_products_tenant_name       ON products (tenant_id, name);
CREATE INDEX idx_products_tenant_vtsz       ON products (tenant_id, vtsz);
CREATE INDEX idx_products_tenant_article    ON products (tenant_id, article_number);
CREATE INDEX idx_products_tenant_status     ON products (tenant_id, status);

-- ─── product_packaging_components ─────────────────────────────────────────────
-- No tenant_id column — tenant isolation is transitive via product_id → products.tenant_id.
-- ALL reads/writes to this table MUST join through products and filter on tenant_id.
CREATE TABLE product_packaging_components (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id                  UUID            NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    material_description        VARCHAR(512)    NOT NULL,
    kf_code                     VARCHAR(16)     NULL,   -- nullable: drafts allowed at DB layer; 8-digit enforced at app layer
    weight_per_unit_kg          NUMERIC(14,6)   NOT NULL CHECK (weight_per_unit_kg >= 0),
    component_order             INT             NOT NULL DEFAULT 0,
    -- PPWR-ready nullable fields (Regulation 2025/40):
    recyclability_grade         VARCHAR(1)      NULL CHECK (recyclability_grade IN ('A','B','C','D')),
    recycled_content_pct        NUMERIC(5,2)    NULL CHECK (recycled_content_pct BETWEEN 0 AND 100),
    reusable                    BOOLEAN         NULL,
    substances_of_concern       JSONB           NULL,
    supplier_declaration_ref    VARCHAR(256)    NULL,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ppc_product_order ON product_packaging_components (product_id, component_order);

-- ─── registry_entry_audit_log ──────────────────────────────────────────────────
-- tenant_id is denormalised here (unlike product_packaging_components) for query-efficient
-- cross-product audit reads (tenant-wide admin views, Story 9.x).
CREATE TABLE registry_entry_audit_log (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id          UUID        NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    field_changed       VARCHAR(128) NOT NULL,
    old_value           TEXT        NULL,
    new_value           TEXT        NULL,
    changed_by_user_id  UUID        NULL REFERENCES users(id),   -- nullable for system-sourced changes
    source              VARCHAR(32) NOT NULL CHECK (source IN (
                            'MANUAL',
                            'AI_SUGGESTED_CONFIRMED',
                            'AI_SUGGESTED_EDITED',
                            'VTSZ_FALLBACK',
                            'NAV_BOOTSTRAP'
                        )),
    timestamp           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_real_tenant_ts   ON registry_entry_audit_log (tenant_id, timestamp DESC);
CREATE INDEX idx_real_product_ts  ON registry_entry_audit_log (product_id, timestamp DESC);

-- ─── updated_at auto-maintenance ───────────────────────────────────────────────
-- Without BEFORE UPDATE triggers, the updated_at columns on products and
-- product_packaging_components will never advance beyond their insert-time value.
-- The trigger function is idempotent (CREATE OR REPLACE) so it is safe to run in
-- multiple migrations if ever needed.

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_products_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_ppc_updated_at
    BEFORE UPDATE ON product_packaging_components
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
