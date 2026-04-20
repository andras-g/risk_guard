-- Story 10.4: Row-level provenance for Registry entries created by the invoice bootstrap.
--
-- Two new nullable columns:
--   product_packaging_components.classifier_source — which path produced this component row
--   products.review_state                          — attention flag for products needing human review
--
-- Idempotency: ADD COLUMN IF NOT EXISTS (Postgres ≥ 9.6).
-- Backfill: pre-10.4 rows (MANUAL, AI_SUGGESTED_CONFIRMED, etc.) keep NULL.
--   NULL classifier_source == unknown provenance (pre-10.4 rows); not a data error.

ALTER TABLE product_packaging_components
    ADD COLUMN IF NOT EXISTS classifier_source VARCHAR(32) NULL
        CHECK (classifier_source IS NULL OR classifier_source IN (
            'MANUAL',
            'MANUAL_WIZARD',
            'AI_SUGGESTED_CONFIRMED',
            'AI_SUGGESTED_EDITED',
            'VTSZ_FALLBACK',
            'NAV_BOOTSTRAP'
        ));

COMMENT ON COLUMN product_packaging_components.classifier_source IS
    'Origin of this component row: NAV_BOOTSTRAP (invoice bootstrap), MANUAL (user hand-edited), VTSZ_FALLBACK (classifier fallback), etc. NULL = pre-10.4 row with unknown provenance.';

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS review_state VARCHAR(32) NULL
        CHECK (review_state IS NULL OR review_state IN (
            'MISSING_PACKAGING'
        ));

COMMENT ON COLUMN products.review_state IS
    'Attention flag. MISSING_PACKAGING = product was bootstrapped from an UNRESOLVED classifier result (zero packaging components). NULL = no review needed.';

-- -- ROLLBACK:
-- ALTER TABLE products DROP COLUMN IF EXISTS review_state;
-- ALTER TABLE product_packaging_components DROP COLUMN IF EXISTS classifier_source;
