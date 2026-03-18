-- Story 2.5 Review Fix (H1): Add CHECK constraint to search_audit_log.sha256_hash
-- Ensures only valid SHA-256 hex hashes (64 lowercase hex chars) or the
-- HASH_UNAVAILABLE sentinel value can be stored. Prevents arbitrary junk
-- from polluting the legal proof audit trail.
--
-- Pattern breakdown:
--   ^[0-9a-f]{64}$  — valid 64-char lowercase hex SHA-256 digest
--   HASH_UNAVAILABLE — sentinel written when hash computation fails (Story 2.5 AC #4)

ALTER TABLE search_audit_log
    ADD CONSTRAINT chk_sha256_hash_valid
    CHECK (sha256_hash ~ '^[0-9a-f]{64}$' OR sha256_hash = 'HASH_UNAVAILABLE');
