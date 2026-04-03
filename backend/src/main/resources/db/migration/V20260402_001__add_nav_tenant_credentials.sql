-- Story 8.1: Per-tenant NAV Online Számla credentials
-- Credentials encrypted at rest via AesFieldEncryptor (AES-256-CBC, PBKDF2 key derivation).
-- password_hash stores SHA-512(rawPassword).toUpperCase() — never the plaintext password.
CREATE TABLE nav_tenant_credentials (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID        NOT NULL UNIQUE,
    login_encrypted  TEXT        NOT NULL,
    password_hash    TEXT        NOT NULL,
    signing_key_enc  TEXT        NOT NULL,
    exchange_key_enc TEXT        NOT NULL,
    tax_number       VARCHAR(8)  NOT NULL,  -- technical user's own 8-digit Hungarian tax number
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
