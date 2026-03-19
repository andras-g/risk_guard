-- Notification Module: add company_name column to watchlist_entries
-- Story 3.6: Watchlist Management (CRUD)
-- Denormalized company name for display performance — stored at time of add.

ALTER TABLE watchlist_entries ADD COLUMN company_name TEXT;
