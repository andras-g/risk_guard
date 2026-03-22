-- Refresh token rotation table for Story 3.13
-- Stores SHA-256 hashes of opaque refresh tokens with family-based rotation tracking.
-- Raw tokens are never persisted; only hashes are stored for validation.

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    family_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Unique index on token_hash — each opaque token maps to exactly one DB row
CREATE UNIQUE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);

-- Lookup by user for revokeAllForUser()
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- Lookup by family for family revocation on reuse detection
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);

-- Cleanup job: DELETE WHERE expires_at < NOW()
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
