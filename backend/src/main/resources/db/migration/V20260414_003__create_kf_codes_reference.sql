-- Story 9.3: KF codes reference table + audit log enrichment columns
-- kf_codes: seed data from 80/2023 Korm. rendelet Annex 1.2 (plastics, glass, metals, paper, wood, textiles)
-- registry_entry_audit_log: add strategy and model_version for AI-sourced classifications

-- ─── kf_codes reference ──────────────────────────────────────────────────────────
CREATE TABLE kf_codes (
    kf_code                 CHAR(8)     PRIMARY KEY,
    material_description_hu TEXT        NOT NULL,
    valid_from              DATE        NOT NULL DEFAULT '2024-01-01',
    valid_to                DATE        NULL
);

-- Seed: common packaging materials from 80/2023 Annex 1.2
-- Plastics (11xxxxxx)
INSERT INTO kf_codes (kf_code, material_description_hu) VALUES
    ('11010101', 'PET – Polietilén-tereftalát'),
    ('11010201', 'HDPE – Nagy sűrűségű polietilén'),
    ('11010301', 'LDPE – Alacsony sűrűségű polietilén'),
    ('11010401', 'PP – Polipropilén'),
    ('11010501', 'PS – Polisztirol'),
    ('11010601', 'EPS – Expandált polisztirol (hab)'),
    ('11010701', 'PVC – Polivinil-klorid'),
    ('11010801', 'PA – Poliamid (Nylon)'),
    ('11010901', 'PC – Polikarbonát'),
    ('11011001', 'ABS – Akrilnitril-butadién-sztirol'),
-- Glass (21xxxxxx)
    ('21010101', 'Üveg – Átlátszó (szín nélküli)'),
    ('21010201', 'Üveg – Színes (barna, zöld)'),
-- Metals (31xxxxxx)
    ('31010101', 'Acél – Általános / bádogdoboz'),
    ('31010201', 'Alumínium – Általános / doboz'),
    ('31010301', 'Ón'),
-- Paper and cardboard (41xxxxxx)
    ('41010101', 'Papír – Általános (nem kezelt)'),
    ('41010201', 'Karton / hullámpapír'),
    ('41010301', 'Finom (kezelt) papír / laminált'),
-- Wood (51xxxxxx)
    ('51010101', 'Fa – Általános (raklap, fadoboz)'),
    ('51010201', 'Furnér / rétegelt lemez'),
-- Natural fibres / textiles (61xxxxxx)
    ('61010101', 'Természetes textil / rost (juta, pamut zsák)'),
-- Composite (71xxxxxx)
    ('71010101', 'Többrétegű kompozit csomagolás (pl. Tetra Pak)');

-- ─── registry_entry_audit_log enrichment ─────────────────────────────────────────
-- Capture AI classifier details (strategy + model version) for AI-sourced rows.
-- Nullable: only populated when source is AI_SUGGESTED_CONFIRMED or AI_SUGGESTED_EDITED.
ALTER TABLE registry_entry_audit_log
    ADD COLUMN IF NOT EXISTS strategy      VARCHAR(32) NULL,
    ADD COLUMN IF NOT EXISTS model_version VARCHAR(64) NULL;

-- Source-level index: supports future admin analytics on AI vs MANUAL vs VTSZ_FALLBACK split
CREATE INDEX IF NOT EXISTS idx_real_source
    ON registry_entry_audit_log (tenant_id, source, timestamp DESC);
