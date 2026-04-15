-- Story 9.4: Add period_start / period_end to epr_exports for OKIRkapu XML export logging.
-- The MOHU CSV export did not record the period — OKIRkapu XML does (required for audit).

ALTER TABLE epr_exports ADD COLUMN period_start DATE NULL;
ALTER TABLE epr_exports ADD COLUMN period_end   DATE NULL;
