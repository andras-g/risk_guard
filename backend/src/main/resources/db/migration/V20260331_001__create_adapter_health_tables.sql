-- Story 6.1: Data Source Health Dashboard (The Heartbeat)
-- Creates tables for circuit breaker health tracking and NAV credential status.

CREATE TABLE adapter_health (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    adapter_name VARCHAR      NOT NULL UNIQUE,
    status      VARCHAR(20),
    last_success_at  TIMESTAMPTZ,
    last_failure_at  TIMESTAMPTZ,
    failure_count    INT NOT NULL DEFAULT 0,
    mtbf_hours  DECIMAL(10, 2),
    updated_at  TIMESTAMPTZ
);

CREATE TABLE nav_credentials (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    adapter_name VARCHAR     NOT NULL UNIQUE,
    status       VARCHAR(20) NOT NULL DEFAULT 'NOT_CONFIGURED',
    expires_at   TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ
);
