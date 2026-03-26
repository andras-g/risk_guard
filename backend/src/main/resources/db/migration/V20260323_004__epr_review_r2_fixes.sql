-- Code review R2 fixes for EPR tables
-- Story 4.0: EPR Module Foundation — Review Follow-ups (R2)
--
-- H1: export_format should use a PostgreSQL ENUM (not VARCHAR) to enforce
--     database-level constraint — architecture doc specifies ENUM: CSV/XLSX.
--     Project pattern: verdict_status and verdict_confidence both use CREATE TYPE AS ENUM.
--
-- M1: epr_calculations.template_id and epr_exports.calculation_id FKs need
--     explicit ON DELETE semantics to prevent silent constraint violations in
--     Stories 4.1 (template CRUD) and 4.4 (export generation).
--
-- M2: Add UNIQUE constraint on epr_configs.version to enable FK references from
--     epr_calculations and epr_exports, then add named FK constraints for
--     config_version referential integrity.
--     NOTE: epr_configs.version already has a UNIQUE index from V20260323_003.
--     We add the FK constraints here now that the unique index is in place.

-- H1: Create export_format ENUM type
CREATE TYPE export_format_type AS ENUM ('CSV', 'XLSX');

-- H1: Alter epr_exports.export_format to use the new ENUM
-- Must cast existing data — currently no rows (seed only inserts epr_configs), so safe
ALTER TABLE epr_exports
    ALTER COLUMN export_format TYPE export_format_type
        USING export_format::export_format_type;

-- M1: epr_exports.calculation_id — set to NULL when a calculation is deleted
-- (preserves the export record for audit purposes even after calculation deletion)
ALTER TABLE epr_exports
    DROP CONSTRAINT IF EXISTS epr_exports_calculation_id_fkey,
    ADD CONSTRAINT fk_epr_exports_calculation
        FOREIGN KEY (calculation_id) REFERENCES epr_calculations(id)
            ON DELETE SET NULL;

-- M1: epr_calculations.template_id — set to NULL when a template is deleted
-- (supports Story 4.1 template deletion — calculations survive as "unclassified")
ALTER TABLE epr_calculations
    DROP CONSTRAINT IF EXISTS epr_calculations_template_id_fkey,
    ADD CONSTRAINT fk_epr_calculations_template
        FOREIGN KEY (template_id) REFERENCES epr_material_templates(id)
            ON DELETE SET NULL;

-- M2: Add FK constraints for config_version referential integrity
-- epr_configs.version already has UNIQUE INDEX (from V20260323_003)
ALTER TABLE epr_calculations
    ADD CONSTRAINT fk_epr_calculations_config_version
        FOREIGN KEY (config_version) REFERENCES epr_configs(version)
            ON DELETE RESTRICT;

ALTER TABLE epr_exports
    ADD CONSTRAINT fk_epr_exports_config_version
        FOREIGN KEY (config_version) REFERENCES epr_configs(version)
            ON DELETE RESTRICT;
