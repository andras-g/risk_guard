-- Story 3.2: Add password_hash column for local (email/password) authentication.
-- NULL for SSO-only users who have no local password.
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255) NULL;
