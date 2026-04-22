-- Story 10.11: Per-Product EPR Scope Flag + Company-Level Default
--
-- Adds two new columns — one on `products`, one on `producer_profiles` — so that each product can be
-- classified as first-placer (in scope), reseller of EU goods (out of scope), or unclassified (still in
-- scope by default, but surfaced via a warning banner). Closes the compliance gap documented in
-- _bmad-output/planning-artifacts/epr-packaging-calculation-gap-2026-04-14.md:47-68.
--
-- Parity guarantee (AC #1): every pre-existing row receives 'UNKNOWN' via the DEFAULT.
-- Idempotency: ADD COLUMN IF NOT EXISTS, DROP CONSTRAINT IF EXISTS + ADD CONSTRAINT with explicit
-- names. Re-running on an already-migrated DB is a safe no-op.
--
-- Compliance-safe default: 'UNKNOWN' is INCLUDED in aggregation totals per AC #3 rationale
-- (under-reporting = regulatory offence 80/2023 §5; over-reporting = cost but legal). The warning
-- banner (Story 10.11 AC #18, #21) makes unclassified rows visible and actionable.

-- 1. products.epr_scope — per-SKU scope flag.
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS epr_scope VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE products
    DROP CONSTRAINT IF EXISTS products_epr_scope_chk;

ALTER TABLE products
    ADD CONSTRAINT products_epr_scope_chk
        CHECK (epr_scope IN ('FIRST_PLACER', 'RESELLER', 'UNKNOWN'));

COMMENT ON COLUMN products.epr_scope IS
    'EPR scope flag per SKU. FIRST_PLACER = liable first placer on HU market (in filing). RESELLER = resold from another EU producer/importer (excluded from filing). UNKNOWN = not yet classified (still included in filing as compliance-safe default; surfaced via warning banner).';

-- Aggregator join-filter hot path (RegistryRepository.loadForAggregation filters on this in every run).
CREATE INDEX IF NOT EXISTS idx_products_tenant_epr_scope
    ON products (tenant_id, epr_scope);

-- 2. producer_profiles.default_epr_scope — company-wide default applied to new products.
ALTER TABLE producer_profiles
    ADD COLUMN IF NOT EXISTS default_epr_scope VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE producer_profiles
    DROP CONSTRAINT IF EXISTS producer_profiles_default_epr_scope_chk;

ALTER TABLE producer_profiles
    ADD CONSTRAINT producer_profiles_default_epr_scope_chk
        CHECK (default_epr_scope IN ('FIRST_PLACER', 'RESELLER', 'UNKNOWN'));

COMMENT ON COLUMN producer_profiles.default_epr_scope IS
    'Default epr_scope applied to products created by RegistryService.createProduct and InvoiceDrivenRegistryBootstrapService when no explicit scope is supplied. Existing products are NOT re-stamped when this value changes.';

-- ROLLBACK:
-- -- To roll this migration back, run the following:
-- ALTER TABLE producer_profiles DROP CONSTRAINT IF EXISTS producer_profiles_default_epr_scope_chk;
-- ALTER TABLE producer_profiles DROP COLUMN IF EXISTS default_epr_scope;
-- DROP INDEX IF EXISTS idx_products_tenant_epr_scope;
-- ALTER TABLE products DROP CONSTRAINT IF EXISTS products_epr_scope_chk;
-- ALTER TABLE products DROP COLUMN IF EXISTS epr_scope;
