-- Story 9.6: Add units_per_product to product_packaging_components.
-- Meaning: how many sold product units does one unit of this packaging contain?
-- Primary = 1, 6-pack = 6, pallet = 480.
ALTER TABLE product_packaging_components
    ADD COLUMN units_per_product INT NOT NULL DEFAULT 1;

COMMENT ON COLUMN product_packaging_components.units_per_product IS
    'Number of product units contained in one unit of this packaging (1 = primary, 6 = 6-pack, 480 = pallet, etc.)';
