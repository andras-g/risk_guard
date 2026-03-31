-- Story 6.2: Manual Adapter Quarantine
-- Adds quarantine flag to adapter_health and creates admin_action_log audit table.

ALTER TABLE adapter_health ADD COLUMN quarantined BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE admin_action_log (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID        NOT NULL,
    action        VARCHAR(50) NOT NULL,
    target        VARCHAR(255) NOT NULL,
    details       JSONB,
    performed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_action_log_performed_at ON admin_action_log (performed_at DESC);
