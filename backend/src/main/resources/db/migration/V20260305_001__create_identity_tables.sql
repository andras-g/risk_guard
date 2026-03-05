-- Identity Tables
CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    tier VARCHAR(50) NOT NULL DEFAULT 'ALAP',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    role VARCHAR(50) NOT NULL DEFAULT 'SME_ADMIN',
    preferred_language VARCHAR(10) NOT NULL DEFAULT 'hu',
    sso_provider VARCHAR(50),
    sso_subject VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tenant_mandates (
    id UUID PRIMARY KEY,
    accountant_user_id UUID NOT NULL REFERENCES users(id),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    valid_from TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE guest_sessions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL, -- synthetic tenant ID
    session_fingerprint VARCHAR(255) NOT NULL,
    companies_checked INT NOT NULL DEFAULT 0,
    daily_checks INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL
);

-- Indexes
CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_tenant_mandates_accountant ON tenant_mandates(accountant_user_id);
CREATE INDEX idx_guest_sessions_expires ON guest_sessions(expires_at);
