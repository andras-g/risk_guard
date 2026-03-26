-- EPR module tables: material templates, configs, calculations, exports
-- Story 4.0: EPR Module Foundation

-- Global EPR configuration table (NOT tenant-scoped).
-- Stores legislation-driven fee schedules that apply to all tenants equally.
CREATE TABLE epr_configs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version           INT NOT NULL,
    config_data       JSONB NOT NULL,
    schema_version    VARCHAR(50),
    schema_verified   BOOLEAN NOT NULL DEFAULT false,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at      TIMESTAMPTZ
);

-- Material templates — tenant-scoped packaging/product templates for EPR calculations.
CREATE TABLE epr_material_templates (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id),
    name              VARCHAR(255) NOT NULL,
    base_weight_grams DECIMAL NOT NULL,
    kf_code           VARCHAR(8),
    verified          BOOLEAN NOT NULL DEFAULT false,
    seasonal          BOOLEAN NOT NULL DEFAULT false,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- EPR calculations — individual fee calculations linked to configs and optionally templates.
CREATE TABLE epr_calculations (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(id),
    config_version          INT NOT NULL,
    template_id             UUID REFERENCES epr_material_templates(id),
    traversal_path          JSONB,
    material_classification VARCHAR(255),
    kf_code                 VARCHAR(8) NOT NULL,
    fee_rate                DECIMAL NOT NULL,
    quantity                DECIMAL,
    total_weight_grams      DECIMAL,
    fee_amount              DECIMAL,
    currency                VARCHAR(3) NOT NULL DEFAULT 'HUF',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- EPR exports — MOHU-ready export files generated from calculations.
CREATE TABLE epr_exports (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id),
    calculation_id    UUID REFERENCES epr_calculations(id),
    config_version    INT NOT NULL,
    export_format     VARCHAR(50) NOT NULL,
    file_hash         VARCHAR(64),
    exported_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_epr_templates_tenant ON epr_material_templates(tenant_id);
CREATE INDEX idx_epr_calculations_tenant ON epr_calculations(tenant_id);
CREATE INDEX idx_epr_configs_version ON epr_configs(version);
