-- Rename seasonal → recurring with logic inversion
-- Old: seasonal = false (default) → material copied every quarter
-- New: recurring = true (default) → material copied every quarter
-- recurring = NOT seasonal — every existing value is flipped

ALTER TABLE epr_material_templates RENAME COLUMN seasonal TO recurring;
UPDATE epr_material_templates SET recurring = NOT recurring;
ALTER TABLE epr_material_templates ALTER COLUMN recurring SET DEFAULT true;
