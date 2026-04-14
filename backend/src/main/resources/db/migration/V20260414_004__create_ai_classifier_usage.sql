-- Story 9.3: AI classifier usage tracking for monthly cap enforcement and cost metering
-- One row per (tenant, year_month). Updated atomically by ClassifierUsageRepository.upsertIncrement().

CREATE TABLE ai_classifier_usage (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id),
    year_month  CHAR(7)     NOT NULL,  -- format: 2026-04  (yyyy-MM)
    call_count  INT         NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Unique index: one row per tenant per month; also used by ON CONFLICT upsert
CREATE UNIQUE INDEX uq_ai_classifier_usage_tenant_month
    ON ai_classifier_usage (tenant_id, year_month);

-- Auto-update updated_at on every write. Reuses set_updated_at() defined in migration 001 (CREATE OR REPLACE).
CREATE TRIGGER trg_ai_classifier_usage_updated_at
    BEFORE UPDATE ON ai_classifier_usage
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
