-- Story 10.4: Async invoice-driven Registry bootstrap job tracking.
-- Creates epr_bootstrap_jobs — one row per bootstrap invocation.
-- Status machine: PENDING → RUNNING → COMPLETED | FAILED_PARTIAL | FAILED | CANCELLED.
--
-- Per-pair REQUIRES_NEW transaction strategy is enforced by NavHttpOutsideTransactionTest
-- and the Story 10.1 tx-pool refactor. Counters reflect committed work at any point.
--
-- Idempotency: CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS so re-running
-- on an already-migrated DB is a safe no-op.

CREATE TABLE IF NOT EXISTS epr_bootstrap_jobs (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL REFERENCES tenants(id),
    status                  VARCHAR(16)     NOT NULL
                                            CHECK (status IN (
                                                'PENDING',
                                                'RUNNING',
                                                'COMPLETED',
                                                'FAILED',
                                                'FAILED_PARTIAL',
                                                'CANCELLED'
                                            )),
    period_from             DATE            NOT NULL,
    period_to               DATE            NOT NULL,
    total_pairs             INT             NOT NULL DEFAULT 0,
    classified_pairs        INT             NOT NULL DEFAULT 0,
    vtsz_fallback_pairs     INT             NOT NULL DEFAULT 0,
    unresolved_pairs        INT             NOT NULL DEFAULT 0,
    created_products        INT             NOT NULL DEFAULT 0,
    deleted_products        INT             NOT NULL DEFAULT 0,
    triggered_by_user_id    UUID            NULL REFERENCES users(id) ON DELETE SET NULL,
    error_message           VARCHAR(1000)   NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMPTZ     NULL,

    CONSTRAINT chk_period_range CHECK (period_from <= period_to)
);

COMMENT ON TABLE epr_bootstrap_jobs IS
    'One row per "Feltöltés számlák alapján" invocation. Workers update counters and status in per-pair REQUIRES_NEW transactions. History is append-only — no rows are deleted after completion.';

-- Partial index for in-flight guard: INSERT … WHERE NOT EXISTS (… WHERE status IN (…)) uses this.
CREATE UNIQUE INDEX IF NOT EXISTS idx_epr_bootstrap_jobs_tenant_inflight
    ON epr_bootstrap_jobs (tenant_id)
    WHERE status IN ('PENDING', 'RUNNING');

-- History index for GET /api/v1/registry/bootstrap-from-invoices?history
CREATE INDEX IF NOT EXISTS idx_epr_bootstrap_jobs_tenant_created
    ON epr_bootstrap_jobs (tenant_id, created_at DESC);

-- -- ROLLBACK:
-- DROP INDEX IF EXISTS idx_epr_bootstrap_jobs_tenant_created;
-- DROP INDEX IF EXISTS idx_epr_bootstrap_jobs_tenant_inflight;
-- DROP TABLE IF EXISTS epr_bootstrap_jobs;
