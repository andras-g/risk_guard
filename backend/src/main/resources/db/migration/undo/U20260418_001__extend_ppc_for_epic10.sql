-- Undo script for V20260418_001__extend_ppc_for_epic10.sql
-- Mirrors the forward migration in reverse order. Round-trip tested on a local dev DB:
--   1. ./gradlew flywayMigrate                                         (apply forward)
--   2. psql -f backend/src/main/resources/db/migration/undo/U20260418_001__extend_ppc_for_epic10.sql  (rollback)
--
-- Safe only while items_per_parent holds integer-expressible values (DEFAULT 1 + no decimal fractions
-- land before this script runs). If Story 10.x persists fractional ratios, the INT narrowing will
-- truncate — intentional for a schema rollback, but document in ops runbook if applied post-10.4.

ALTER TABLE product_packaging_components
    ALTER COLUMN items_per_parent DROP DEFAULT;

ALTER TABLE product_packaging_components
    ALTER COLUMN items_per_parent TYPE INT
    USING items_per_parent::INT;

ALTER TABLE product_packaging_components
    ALTER COLUMN items_per_parent SET DEFAULT 1;

ALTER TABLE product_packaging_components
    ALTER COLUMN items_per_parent SET NOT NULL;

ALTER TABLE product_packaging_components
    RENAME COLUMN items_per_parent TO units_per_product;

COMMENT ON COLUMN product_packaging_components.units_per_product IS
    'Number of product units contained in one unit of this packaging (1 = primary, 6 = 6-pack, 480 = pallet, etc.)';

DROP INDEX IF EXISTS idx_ppc_material_template;

ALTER TABLE product_packaging_components
    DROP COLUMN IF EXISTS material_template_id;

ALTER TABLE product_packaging_components
    DROP COLUMN IF EXISTS wrapping_level;
