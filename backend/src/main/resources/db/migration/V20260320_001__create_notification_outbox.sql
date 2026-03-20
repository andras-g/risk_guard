-- Story 3.8: Notification outbox table for at-least-once email delivery.
-- Follows the transactional outbox pattern: records are inserted within the
-- event listener's transaction, then polled and sent by OutboxProcessor.

CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID REFERENCES users(id),
    type VARCHAR(30) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ
);

-- Outbox processor query: find PENDING records ready for retry
CREATE INDEX idx_outbox_status ON notification_outbox (status, next_retry_at);

-- Per-tenant queries (e.g., daily alert count for digest mode)
CREATE INDEX idx_outbox_tenant ON notification_outbox (tenant_id);
