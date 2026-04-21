-- Story 10.8: Aggregation audit log table
-- Captures AGGREGATION_RUN, PROVENANCE_FETCH, and CSV_EXPORT events.

CREATE TABLE IF NOT EXISTS aggregation_audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    event_type      VARCHAR(50)  NOT NULL,
    period_start    DATE,
    period_end      DATE,
    resolved_count  INT,
    unresolved_count INT,
    duration_ms     BIGINT,
    page            INT,
    page_size       INT,
    performed_by_user_id UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_aggregation_audit_log_tenant_created
    ON aggregation_audit_log (tenant_id, created_at DESC);
