-- Story 10.1 (Epic 10): Extend product_packaging_components for multi-layer packaging identity
-- and introduce a nullable FK into the internal material-template library.
--
-- Three changes (in order):
--   1. ADD wrapping_level INT NOT NULL DEFAULT 1  (1=primary, 2=secondary/collector, 3=tertiary/transport).
--   2. ADD material_template_id UUID NULL REFERENCES epr_material_templates(id) ON DELETE RESTRICT.
--   3. RENAME units_per_product → items_per_parent, THEN widen INT → NUMERIC(12,4), preserving NOT NULL DEFAULT 1.
--
-- Parity guarantee (AC #3): every existing row ends up with wrapping_level=1, material_template_id=NULL,
-- and items_per_parent = (old units_per_product)::NUMERIC(12,4). The INT→NUMERIC cast is lossless for
-- integer literals, so no rounding change.
--
-- Regression guarantee (AC #21): OkirkapuXmlExporter's per-line-item formula
-- (weightPerUnitKg × quantity / itemsPerParent) is byte-for-byte identical for pre-existing data.
--
-- Idempotency (AC #2): ADD COLUMN uses IF NOT EXISTS (Postgres ≥ 9.6). RENAME is guarded via
-- information_schema.columns. Retype is a no-op on NUMERIC(12,4). Re-running this migration on an
-- already-migrated DB is a safe no-op.

-- 1. wrapping_level — multi-layer packaging hierarchy identity.
ALTER TABLE product_packaging_components
    ADD COLUMN IF NOT EXISTS wrapping_level INT NOT NULL DEFAULT 1
        CHECK (wrapping_level BETWEEN 1 AND 3);

COMMENT ON COLUMN product_packaging_components.wrapping_level IS
    'Packaging layer in the product hierarchy: 1=primary (direct product contact), 2=secondary/collector, 3=tertiary/transport. Drives aggregation in OKIRkapu XML export.';

-- 2. material_template_id — nullable FK into the internal material-template library.
--    RESTRICT guarantees the template cannot be deleted while referenced.
--    REST layer maps the resulting DataIntegrityViolationException to RFC-7807 (Task 4).
ALTER TABLE product_packaging_components
    ADD COLUMN IF NOT EXISTS material_template_id UUID NULL
        REFERENCES epr_material_templates(id) ON DELETE RESTRICT;

COMMENT ON COLUMN product_packaging_components.material_template_id IS
    'Optional link to a reusable building-block template in epr_material_templates. Nullable: free-text material_description remains the primary user-facing label. ON DELETE RESTRICT prevents silent orphan audit trails.';

CREATE INDEX IF NOT EXISTS idx_ppc_material_template
    ON product_packaging_components (material_template_id)
    WHERE material_template_id IS NOT NULL;

-- 3a. Rename units_per_product → items_per_parent. Multi-layer semantics: a secondary pack's
--     "parent" is a primary pack, not the product; "per parent" clarifies the ratio.
--     Idempotency: guarded so re-runs on an already-migrated DB are a no-op.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'product_packaging_components'
          AND column_name = 'units_per_product'
    ) THEN
        ALTER TABLE product_packaging_components
            RENAME COLUMN units_per_product TO items_per_parent;
    END IF;
END
$$;

-- 3b. Widen INT → NUMERIC(12,4). USING clause is a no-op round-trip on integer literals.
--     Preserve NOT NULL DEFAULT 1 semantics across the retype.
--     Idempotency: these are no-ops when already applied (NUMERIC(12,4), NOT NULL, DEFAULT 1).
ALTER TABLE product_packaging_components
    ALTER COLUMN items_per_parent DROP DEFAULT;

ALTER TABLE product_packaging_components
    ALTER COLUMN items_per_parent TYPE NUMERIC(12,4)
    USING items_per_parent::NUMERIC(12,4);

ALTER TABLE product_packaging_components
    ALTER COLUMN items_per_parent SET DEFAULT 1;

ALTER TABLE product_packaging_components
    ALTER COLUMN items_per_parent SET NOT NULL;

-- 3c. Domain CHECK — items_per_parent must be strictly positive. Bean Validation enforces this
--     on the DTO path; the DB-level CHECK protects direct-SQL paths (seed migrations, admin
--     scripts, future endpoints) from inserting 0/negative values that would cause
--     ArithmeticException (divide-by-zero) in OkirkapuXmlExporter.processLineItem (R3-P10).
ALTER TABLE product_packaging_components
    DROP CONSTRAINT IF EXISTS items_per_parent_positive;

ALTER TABLE product_packaging_components
    ADD CONSTRAINT items_per_parent_positive CHECK (items_per_parent > 0);

COMMENT ON COLUMN product_packaging_components.items_per_parent IS
    'Units of this component per one unit of its parent in the packaging hierarchy — primary: per product; secondary: per primary pack; tertiary: per secondary/pallet layer. NUMERIC(12,4) supports non-integer ratios (e.g., 0.5 for half-pallet covers). CHECK > 0 enforces positive at DB boundary.';
