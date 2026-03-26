-- Story 5.1: Denormalize latest SHA-256 hash onto watchlist_entries.
-- Allows the watchlist API to return the hash without cross-module SQL joins.
-- Updated by PartnerStatusChangedListener when a PartnerStatusChanged event fires.
ALTER TABLE watchlist_entries ADD COLUMN latest_sha256_hash VARCHAR(64);
