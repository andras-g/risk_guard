-- Demo Data Seed (Repeatable Migration)
-- =======================================
-- Seeds two demo users for presenting every feature of RiskGuard:
--
--  1. demo@riskguard.hu (SME_ADMIN) — "Bemutató Kereskedelmi Kft."
--     - 5 partner companies on watchlist (RELIABLE, AT_RISK, TAX_SUSPENDED)
--     - Company snapshots and verdicts for all 5 partners
--     - 4 EPR material templates (3 verified, 1 pending classification)
--     - 3 EPR calculations for Q1 2026 with a MOHU export
--     - Search audit log entries
--
--  2. accountant@riskguard.hu (ACCOUNTANT) — "Precíz Könyvelő Iroda Kft."
--     - 3 client tenants via tenant_mandates:
--         a. Zöld Élelmiszer Kft. (PRO_EPR) — food/beverage, EPR-heavy
--         b. Prémium Bútor Zrt.  (PRO_EPR) — furniture manufacturer
--         c. TechStart Kft.      (ALAP)    — startup, just started
--     - Each client has watchlist entries, snapshots, verdicts, EPR data
--
-- UUID scheme (deterministic, idempotent):
--   b000: demo SME tenant/user
--   c000: demo SME partner snapshots, verdicts, watchlist, EPR, audit log
--   b010: accountant tenant/user
--   b020: client tenants
--   b030: tenant mandates
--   d000: client tenant 1 data
--   d050: client tenant 2 data
--   d100: client tenant 3 data
--
-- All INSERTs use ON CONFLICT DO NOTHING for idempotency.

-- =============================================================================
-- SECTION 1: Demo SME — Tenant & User
-- =============================================================================
INSERT INTO tenants (id, name, tier)
VALUES ('00000000-0000-4000-b000-000000000001', 'Bemutató Kereskedelmi Kft.', 'PRO_EPR')
ON CONFLICT (id) DO NOTHING;

-- password: Demo1234!
INSERT INTO users (id, tenant_id, email, name, role, preferred_language, sso_provider, sso_subject, password_hash)
VALUES (
    '00000000-0000-4000-b000-000000000002',
    '00000000-0000-4000-b000-000000000001',
    'demo@riskguard.hu',
    'Demo Felhasználó',
    'SME_ADMIN',
    'hu',
    'local',
    NULL,
    '$2b$10$7lWn1cbOo0sYdV55eyfXZ.691xj7rdTjB3XaNhYOjtReUpBEGBbIS'
)
ON CONFLICT (id) DO UPDATE SET
    sso_provider  = EXCLUDED.sso_provider,
    sso_subject   = EXCLUDED.sso_subject,
    password_hash = EXCLUDED.password_hash;

-- password: Admin1234!
-- PLATFORM_ADMIN demo user — access to EPR config, GDPR audit, and quarantine
INSERT INTO users (id, tenant_id, email, name, role, preferred_language, sso_provider, sso_subject, password_hash)
VALUES (
    '00000000-0000-4000-b000-000000000009',
    '00000000-0000-4000-b000-000000000001',
    'platform-admin@riskguard.hu',
    'Platform Admin',
    'PLATFORM_ADMIN',
    'hu',
    'local',
    NULL,
    '$2b$10$7lWn1cbOo0sYdV55eyfXZ.691xj7rdTjB3XaNhYOjtReUpBEGBbIS'
)
ON CONFLICT (id) DO UPDATE SET
    sso_provider  = EXCLUDED.sso_provider,
    sso_subject   = EXCLUDED.sso_subject,
    password_hash = EXCLUDED.password_hash;

-- =============================================================================
-- SECTION 2: Demo SME — Partner Snapshots
-- =============================================================================
-- company_snapshots for the 5 watchlist partners of the demo SME tenant
-- Snapshot data JSONB matches DemoCompanyFixtures / SnapshotDataParser structure
INSERT INTO company_snapshots (id, tenant_id, tax_number, snapshot_data, created_at, updated_at) VALUES
(
    '00000000-0000-4000-c000-000000000001',
    '00000000-0000-4000-b000-000000000001',
    '12345678142',
    '{"available":true,"companyName":"Példa Kereskedelmi Kft.","registrationNumber":"01-09-123456","taxNumberStatus":"ACTIVE","hasPublicDebt":false,"hasInsolvencyProceedings":false,"debtAmount":0,"debtCurrency":"HUF","status":"ACTIVE"}'::jsonb,
    now() - INTERVAL '2 hours', now() - INTERVAL '2 hours'
),
(
    '00000000-0000-4000-c000-000000000002',
    '00000000-0000-4000-b000-000000000001',
    '99887766113',
    '{"available":true,"companyName":"Megbízható Építő Zrt.","registrationNumber":"13-10-041234","taxNumberStatus":"ACTIVE","hasPublicDebt":false,"hasInsolvencyProceedings":false,"debtAmount":0,"debtCurrency":"HUF","status":"ACTIVE"}'::jsonb,
    now() - INTERVAL '3 hours', now() - INTERVAL '3 hours'
),
(
    '00000000-0000-4000-c000-000000000003',
    '00000000-0000-4000-b000-000000000001',
    '11223344142',
    '{"available":true,"companyName":"Adós Szolgáltató Bt.","registrationNumber":"01-06-789012","taxNumberStatus":"ACTIVE","hasPublicDebt":true,"hasInsolvencyProceedings":false,"debtAmount":2450000,"debtCurrency":"HUF","status":"ACTIVE"}'::jsonb,
    now() - INTERVAL '1 hour', now() - INTERVAL '1 hour'
),
(
    '00000000-0000-4000-c000-000000000004',
    '00000000-0000-4000-b000-000000000001',
    '55667788075',
    '{"available":true,"companyName":"Csődben Lévő Kft.","registrationNumber":"07-09-345678","taxNumberStatus":"ACTIVE","hasPublicDebt":false,"hasInsolvencyProceedings":true,"debtAmount":0,"debtCurrency":"HUF","status":"ACTIVE"}'::jsonb,
    now() - INTERVAL '10 hours', now() - INTERVAL '10 hours'
),
(
    '00000000-0000-4000-c000-000000000005',
    '00000000-0000-4000-b000-000000000001',
    '33445566142',
    '{"available":true,"companyName":"Felfüggesztett Adószámú Kft.","registrationNumber":"01-09-333444","taxNumberStatus":"SUSPENDED","hasPublicDebt":false,"hasInsolvencyProceedings":false,"debtAmount":0,"debtCurrency":"HUF","status":"INACTIVE"}'::jsonb,
    now() - INTERVAL '30 minutes', now() - INTERVAL '30 minutes'
)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- SECTION 3: Demo SME — Partner Verdicts
-- =============================================================================
INSERT INTO verdicts (id, tenant_id, snapshot_id, status, confidence, created_at, updated_at) VALUES
(
    '00000000-0000-4000-c000-000000000011',
    '00000000-0000-4000-b000-000000000001',
    '00000000-0000-4000-c000-000000000001',
    'RELIABLE', 'FRESH',
    now() - INTERVAL '2 hours', now() - INTERVAL '2 hours'
),
(
    '00000000-0000-4000-c000-000000000012',
    '00000000-0000-4000-b000-000000000001',
    '00000000-0000-4000-c000-000000000002',
    'RELIABLE', 'FRESH',
    now() - INTERVAL '3 hours', now() - INTERVAL '3 hours'
),
(
    '00000000-0000-4000-c000-000000000013',
    '00000000-0000-4000-b000-000000000001',
    '00000000-0000-4000-c000-000000000003',
    'AT_RISK', 'FRESH',
    now() - INTERVAL '1 hour', now() - INTERVAL '1 hour'
),
(
    '00000000-0000-4000-c000-000000000014',
    '00000000-0000-4000-b000-000000000001',
    '00000000-0000-4000-c000-000000000004',
    'AT_RISK', 'STALE',
    now() - INTERVAL '10 hours', now() - INTERVAL '10 hours'
),
(
    '00000000-0000-4000-c000-000000000015',
    '00000000-0000-4000-b000-000000000001',
    '00000000-0000-4000-c000-000000000005',
    'TAX_SUSPENDED', 'FRESH',
    now() - INTERVAL '30 minutes', now() - INTERVAL '30 minutes'
)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- SECTION 4: Demo SME — Watchlist Entries
-- =============================================================================
INSERT INTO watchlist_entries (
    id, tenant_id, tax_number, label, company_name,
    last_verdict_status, last_checked_at, latest_sha256_hash, previous_verdict_status,
    created_at, updated_at
) VALUES
(
    '00000000-0000-4000-c000-000000000021',
    '00000000-0000-4000-b000-000000000001',
    '12345678142', 'Fő szállító', 'Példa Kereskedelmi Kft.',
    'RELIABLE', now() - INTERVAL '2 hours',
    'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2', NULL,
    now() - INTERVAL '30 days', now() - INTERVAL '2 hours'
),
(
    '00000000-0000-4000-c000-000000000022',
    '00000000-0000-4000-b000-000000000001',
    '99887766113', 'Építési alvállalkozó', 'Megbízható Építő Zrt.',
    'RELIABLE', now() - INTERVAL '3 hours',
    'b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3', NULL,
    now() - INTERVAL '45 days', now() - INTERVAL '3 hours'
),
(
    '00000000-0000-4000-c000-000000000023',
    '00000000-0000-4000-b000-000000000001',
    '11223344142', 'IT tanácsadó', 'Adós Szolgáltató Bt.',
    'AT_RISK', now() - INTERVAL '1 hour',
    'c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4', 'RELIABLE',
    now() - INTERVAL '60 days', now() - INTERVAL '1 hour'
),
(
    '00000000-0000-4000-c000-000000000024',
    '00000000-0000-4000-b000-000000000001',
    '55667788075', 'Élelmiszer szállító', 'Csődben Lévő Kft.',
    'AT_RISK', now() - INTERVAL '10 hours',
    'd4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5', 'AT_RISK',
    now() - INTERVAL '90 days', now() - INTERVAL '10 hours'
),
(
    '00000000-0000-4000-c000-000000000025',
    '00000000-0000-4000-b000-000000000001',
    '33445566142', 'Irodaszer beszerzés', 'Felfüggesztett Adószámú Kft.',
    'TAX_SUSPENDED', now() - INTERVAL '30 minutes',
    'e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6', 'RELIABLE',
    now() - INTERVAL '20 days', now() - INTERVAL '30 minutes'
)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- SECTION 5: Demo SME — Search Audit Log
-- =============================================================================
-- Audit log entries showing search history for the demo SME user
INSERT INTO search_audit_log (
    id, tenant_id, tax_number, searched_by,
    sha256_hash, disclaimer_text, check_source, data_source_mode, verdict_id, searched_at
) VALUES
(
    '00000000-0000-4000-c000-000000000061',
    '00000000-0000-4000-b000-000000000001',
    '12345678142',
    '00000000-0000-4000-b000-000000000002',
    'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2',
    'Az adatok tájékoztató jellegűek. A RiskGuard nem vállal felelősséget az adatok pontosságáért.',
    'MANUAL', 'DEMO',
    '00000000-0000-4000-c000-000000000011',
    now() - INTERVAL '2 hours'
),
(
    '00000000-0000-4000-c000-000000000062',
    '00000000-0000-4000-b000-000000000001',
    '11223344142',
    '00000000-0000-4000-b000-000000000002',
    'c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4',
    'Az adatok tájékoztató jellegűek. A RiskGuard nem vállal felelősséget az adatok pontosságáért.',
    'MANUAL', 'DEMO',
    '00000000-0000-4000-c000-000000000013',
    now() - INTERVAL '1 hour'
),
(
    '00000000-0000-4000-c000-000000000063',
    '00000000-0000-4000-b000-000000000001',
    '33445566142',
    '00000000-0000-4000-b000-000000000002',
    'e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6',
    'Az adatok tájékoztató jellegűek. A RiskGuard nem vállal felelősséget az adatok pontosságáért.',
    'MANUAL', 'DEMO',
    '00000000-0000-4000-c000-000000000015',
    now() - INTERVAL '30 minutes'
)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- SECTION 6: Demo SME — EPR Material Templates
-- =============================================================================
-- 4 templates: 3 verified (plastic bottle, cardboard box, aluminium can), 1 pending
-- KF codes (8-digit): [product_stream 2d][material 2d][group 2d][subgroup 2d]
--   12020101 = mandatory-deposit / plastic / PET bottle / default
--   11010101 = non-deposit / paper+cardboard / consumer / default
--   12040301 = mandatory-deposit / metal / metal can / default
--   11020101 = non-deposit / plastic / consumer / default (unverified)
INSERT INTO epr_material_templates (
    id, tenant_id, name, base_weight_grams, kf_code, verified, recurring, created_at, updated_at
) VALUES
(
    '00000000-0000-4000-c000-000000000031',
    '00000000-0000-4000-b000-000000000001',
    'PET palack 0,5L', 25.0, '12020101', true, true,
    now() - INTERVAL '20 days', now() - INTERVAL '20 days'
),
(
    '00000000-0000-4000-c000-000000000032',
    '00000000-0000-4000-b000-000000000001',
    'Kartondoboz (vegyes)', 350.0, '11010101', true, true,
    now() - INTERVAL '20 days', now() - INTERVAL '20 days'
),
(
    '00000000-0000-4000-c000-000000000033',
    '00000000-0000-4000-b000-000000000001',
    'Alumínium doboz 0,33L', 15.0, '12040301', true, false,
    now() - INTERVAL '15 days', now() - INTERVAL '15 days'
),
(
    '00000000-0000-4000-c000-000000000034',
    '00000000-0000-4000-b000-000000000001',
    'Műanyag fólia zacskó', 5.0, NULL, false, false,
    now() - INTERVAL '3 days', now() - INTERVAL '3 days'
)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- SECTION 7: Demo SME — EPR Calculations & Export
-- =============================================================================
-- EPR calculations for Q1 2026 (3 verified templates → 3 calculations)
-- fee_rate from epr_configs version 1:
--   12020101 → plastic (product 12, material 02) → 1202 → 42.89 HUF/kg
--   11010101 → paper (product 11, material 01)   → 1101 → 20.44 HUF/kg
--   12040301 → metal (product 12, material 04)   → 1204 → 17.37 HUF/kg
INSERT INTO epr_calculations (
    id, tenant_id, config_version, template_id,
    traversal_path, material_classification, kf_code,
    fee_rate, quantity, total_weight_grams, fee_amount, currency,
    confidence, override_kf_code, override_reason, created_at
) VALUES
(
    '00000000-0000-4000-c000-000000000041',
    '00000000-0000-4000-b000-000000000001',
    1,
    '00000000-0000-4000-c000-000000000031',
    '[{"step":"product_stream","selected":"12"},{"step":"material","selected":"02"},{"step":"group","selected":"01"},{"step":"subgroup","selected":"01"}]'::jsonb,
    'Kötelezően visszaváltási díjas egyszer használatos csomagolás / Műanyag / PET palack',
    '12020101',
    42.89, 50000, 1250000.0, 53613, 'HUF',
    'HIGH', NULL, NULL,
    now() - INTERVAL '10 days'
),
(
    '00000000-0000-4000-c000-000000000042',
    '00000000-0000-4000-b000-000000000001',
    1,
    '00000000-0000-4000-c000-000000000032',
    '[{"step":"product_stream","selected":"11"},{"step":"material","selected":"01"},{"step":"group","selected":"01"},{"step":"subgroup","selected":"01"}]'::jsonb,
    'Nem kötelezően visszaváltási díjas egyszer használatos csomagolás / Papír és karton / Fogyasztói',
    '11010101',
    20.44, 10000, 3500000.0, 71540, 'HUF',
    'HIGH', NULL, NULL,
    now() - INTERVAL '10 days'
),
(
    '00000000-0000-4000-c000-000000000043',
    '00000000-0000-4000-b000-000000000001',
    1,
    '00000000-0000-4000-c000-000000000033',
    '[{"step":"product_stream","selected":"12"},{"step":"material","selected":"04"},{"step":"group","selected":"03"},{"step":"subgroup","selected":"01"}]'::jsonb,
    'Kötelezően visszaváltási díjas egyszer használatos csomagolás / Fém: vas, acél és alumínium / Fém doboz',
    '12040301',
    17.37, 30000, 450000.0, 7817, 'HUF',
    'MEDIUM', NULL, NULL,
    now() - INTERVAL '10 days'
)
ON CONFLICT (id) DO NOTHING;

-- EPR export (MOHU-ready CSV for Q1 2026)
INSERT INTO epr_exports (
    id, tenant_id, calculation_id, config_version, export_format, file_hash, exported_at
) VALUES
(
    '00000000-0000-4000-c000-000000000051',
    '00000000-0000-4000-b000-000000000001',
    '00000000-0000-4000-c000-000000000041',
    1,
    'CSV',
    'f1e2d3c4b5a6f1e2d3c4b5a6f1e2d3c4b5a6f1e2d3c4b5a6f1e2d3c4b5a6f1e2',
    now() - INTERVAL '9 days'
)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- SECTION 8: Accountant — Tenant, User & Client Tenants
-- =============================================================================
-- Accountant's own firm
INSERT INTO tenants (id, name, tier)
VALUES ('00000000-0000-4000-b000-000000000010', 'Precíz Könyvelő Iroda Kft.', 'ALAP')
ON CONFLICT (id) DO NOTHING;

-- password: Demo1234!
INSERT INTO users (id, tenant_id, email, name, role, preferred_language, sso_provider, sso_subject, password_hash)
VALUES (
    '00000000-0000-4000-b000-000000000011',
    '00000000-0000-4000-b000-000000000010',
    'accountant@riskguard.hu',
    'Demo Könyvelő',
    'ACCOUNTANT',
    'hu',
    'local',
    NULL,
    '$2b$10$c.MltKsOFtsU0Lov34xq7eEGBtuAXFCf/W6PaI9xNS15JWX5XB4TK'
)
ON CONFLICT (id) DO UPDATE SET
    sso_provider  = EXCLUDED.sso_provider,
    sso_subject   = EXCLUDED.sso_subject,
    password_hash = EXCLUDED.password_hash;

-- Three client tenants managed by the accountant
INSERT INTO tenants (id, name, tier) VALUES
('00000000-0000-4000-b000-000000000020', 'Zöld Élelmiszer Kft.', 'PRO_EPR'),
('00000000-0000-4000-b000-000000000021', 'Prémium Bútor Zrt.', 'PRO_EPR'),
('00000000-0000-4000-b000-000000000022', 'TechStart Kft.', 'ALAP')
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- SECTION 9: Tenant Mandates (Accountant → Clients)
-- =============================================================================
INSERT INTO tenant_mandates (id, accountant_user_id, tenant_id, valid_from, valid_to, created_at)
VALUES
(
    '00000000-0000-4000-b000-000000000030',
    '00000000-0000-4000-b000-000000000011',
    '00000000-0000-4000-b000-000000000020',
    '2026-01-01 00:00:00+00', NULL,
    now() - INTERVAL '90 days'
),
(
    '00000000-0000-4000-b000-000000000031',
    '00000000-0000-4000-b000-000000000011',
    '00000000-0000-4000-b000-000000000021',
    '2026-02-01 00:00:00+00', NULL,
    now() - INTERVAL '59 days'
),
(
    '00000000-0000-4000-b000-000000000032',
    '00000000-0000-4000-b000-000000000011',
    '00000000-0000-4000-b000-000000000022',
    '2026-03-15 00:00:00+00', NULL,
    now() - INTERVAL '17 days'
)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- SECTION 10: Client 1 — Zöld Élelmiszer Kft. (PRO_EPR)
-- =============================================================================
-- Snapshots for Zöld Élelmiszer's 3 watchlist partners
INSERT INTO company_snapshots (id, tenant_id, tax_number, snapshot_data, created_at, updated_at) VALUES
(
    '00000000-0000-4000-d000-000000000001',
    '00000000-0000-4000-b000-000000000020',
    '77889900142',
    '{"available":true,"companyName":"Megbízható Szállító Kft.","registrationNumber":"01-09-556677","taxNumberStatus":"ACTIVE","hasPublicDebt":false,"hasInsolvencyProceedings":false,"debtAmount":0,"debtCurrency":"HUF","status":"ACTIVE"}'::jsonb,
    now() - INTERVAL '4 hours', now() - INTERVAL '4 hours'
),
(
    '00000000-0000-4000-d000-000000000002',
    '00000000-0000-4000-b000-000000000020',
    '22334455113',
    '{"available":true,"companyName":"Friss Partner Kft.","registrationNumber":"13-09-998877","taxNumberStatus":"ACTIVE","hasPublicDebt":false,"hasInsolvencyProceedings":false,"debtAmount":0,"debtCurrency":"HUF","status":"ACTIVE"}'::jsonb,
    now() - INTERVAL '5 hours', now() - INTERVAL '5 hours'
),
(
    '00000000-0000-4000-d000-000000000003',
    '00000000-0000-4000-b000-000000000020',
    '44556677075',
    '{"available":true,"companyName":"Kockázatos Cég Kft.","registrationNumber":"07-09-111222","taxNumberStatus":"ACTIVE","hasPublicDebt":true,"hasInsolvencyProceedings":true,"debtAmount":5780000,"debtCurrency":"HUF","status":"ACTIVE"}'::jsonb,
    now() - INTERVAL '2 hours', now() - INTERVAL '2 hours'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO verdicts (id, tenant_id, snapshot_id, status, confidence, created_at, updated_at) VALUES
(
    '00000000-0000-4000-d000-000000000011',
    '00000000-0000-4000-b000-000000000020',
    '00000000-0000-4000-d000-000000000001',
    'RELIABLE', 'FRESH', now() - INTERVAL '4 hours', now() - INTERVAL '4 hours'
),
(
    '00000000-0000-4000-d000-000000000012',
    '00000000-0000-4000-b000-000000000020',
    '00000000-0000-4000-d000-000000000002',
    'RELIABLE', 'FRESH', now() - INTERVAL '5 hours', now() - INTERVAL '5 hours'
),
(
    '00000000-0000-4000-d000-000000000013',
    '00000000-0000-4000-b000-000000000020',
    '00000000-0000-4000-d000-000000000003',
    'AT_RISK', 'FRESH', now() - INTERVAL '2 hours', now() - INTERVAL '2 hours'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO watchlist_entries (
    id, tenant_id, tax_number, label, company_name,
    last_verdict_status, last_checked_at, latest_sha256_hash, previous_verdict_status,
    created_at, updated_at
) VALUES
(
    '00000000-0000-4000-d000-000000000021',
    '00000000-0000-4000-b000-000000000020',
    '77889900142', 'Alapanyag szállító', 'Megbízható Szállító Kft.',
    'RELIABLE', now() - INTERVAL '4 hours',
    'f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1f6e5', NULL,
    now() - INTERVAL '60 days', now() - INTERVAL '4 hours'
),
(
    '00000000-0000-4000-d000-000000000022',
    '00000000-0000-4000-b000-000000000020',
    '22334455113', 'Csomagolóanyag', 'Friss Partner Kft.',
    'RELIABLE', now() - INTERVAL '5 hours',
    'a6b5c4d3e2f1a6b5c4d3e2f1a6b5c4d3e2f1a6b5c4d3e2f1a6b5c4d3e2f1a6b5', NULL,
    now() - INTERVAL '30 days', now() - INTERVAL '5 hours'
),
(
    '00000000-0000-4000-d000-000000000023',
    '00000000-0000-4000-b000-000000000020',
    '44556677075', 'Logisztikai partner', 'Kockázatos Cég Kft.',
    'AT_RISK', now() - INTERVAL '2 hours',
    'b5c4d3e2f1a6b5c4d3e2f1a6b5c4d3e2f1a6b5c4d3e2f1a6b5c4d3e2f1a6b5c4', 'RELIABLE',
    now() - INTERVAL '75 days', now() - INTERVAL '2 hours'
)
ON CONFLICT (id) DO NOTHING;

-- EPR templates for Zöld Élelmiszer: plastic bottle, glass jar, cardboard transport box
INSERT INTO epr_material_templates (
    id, tenant_id, name, base_weight_grams, kf_code, verified, recurring, created_at, updated_at
) VALUES
(
    '00000000-0000-4000-d000-000000000031',
    '00000000-0000-4000-b000-000000000020',
    'PET palack 1,5L', 38.0, '12020101', true, true,
    now() - INTERVAL '25 days', now() - INTERVAL '25 days'
),
(
    '00000000-0000-4000-d000-000000000032',
    '00000000-0000-4000-b000-000000000020',
    'Üvegpalack 0,75L', 350.0, '12050201', true, true,
    now() - INTERVAL '25 days', now() - INTERVAL '25 days'
),
(
    '00000000-0000-4000-d000-000000000033',
    '00000000-0000-4000-b000-000000000020',
    'Karton szállítódoboz', 600.0, '11010301', true, false,
    now() - INTERVAL '20 days', now() - INTERVAL '20 days'
)
ON CONFLICT (id) DO NOTHING;

-- EPR calculations for Zöld Élelmiszer Q1 2026
-- 12020101 (plastic) → 42.89, 12050201 (glass bottle) → 10.22 HUF/kg
INSERT INTO epr_calculations (
    id, tenant_id, config_version, template_id,
    traversal_path, material_classification, kf_code,
    fee_rate, quantity, total_weight_grams, fee_amount, currency,
    confidence, override_kf_code, override_reason, created_at
) VALUES
(
    '00000000-0000-4000-d000-000000000041',
    '00000000-0000-4000-b000-000000000020',
    1, '00000000-0000-4000-d000-000000000031',
    '[{"step":"product_stream","selected":"12"},{"step":"material","selected":"02"},{"step":"group","selected":"01"},{"step":"subgroup","selected":"01"}]'::jsonb,
    'Kötelezően visszaváltási díjas egyszer használatos csomagolás / Műanyag / PET palack',
    '12020101', 42.89, 120000, 4560000.0, 195581, 'HUF',
    'HIGH', NULL, NULL, now() - INTERVAL '8 days'
),
(
    '00000000-0000-4000-d000-000000000042',
    '00000000-0000-4000-b000-000000000020',
    1, '00000000-0000-4000-d000-000000000032',
    '[{"step":"product_stream","selected":"12"},{"step":"material","selected":"05"},{"step":"group","selected":"02"},{"step":"subgroup","selected":"01"}]'::jsonb,
    'Kötelezően visszaváltási díjas egyszer használatos csomagolás / Üveg / Üveg palack',
    '12050201', 10.22, 30000, 10500000.0, 107310, 'HUF',
    'HIGH', NULL, NULL, now() - INTERVAL '8 days'
)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- SECTION 11: Client 2 — Prémium Bútor Zrt. (PRO_EPR)
-- =============================================================================
-- Snapshots for Prémium Bútor's 2 watchlist partners
INSERT INTO company_snapshots (id, tenant_id, tax_number, snapshot_data, created_at, updated_at) VALUES
(
    '00000000-0000-4000-d000-000000000051',
    '00000000-0000-4000-b000-000000000021',
    '66778899142',
    '{"available":true,"companyName":"Fa Alapanyag Kft.","registrationNumber":"01-09-667788","taxNumberStatus":"ACTIVE","hasPublicDebt":false,"hasInsolvencyProceedings":false,"debtAmount":0,"debtCurrency":"HUF","status":"ACTIVE"}'::jsonb,
    now() - INTERVAL '6 hours', now() - INTERVAL '6 hours'
),
(
    '00000000-0000-4000-d000-000000000052',
    '00000000-0000-4000-b000-000000000021',
    '55443322113',
    '{"available":true,"companyName":"Vegyi Anyag Bt.","registrationNumber":"13-06-554433","taxNumberStatus":"ACTIVE","hasPublicDebt":false,"hasInsolvencyProceedings":false,"debtAmount":0,"debtCurrency":"HUF","status":"ACTIVE"}'::jsonb,
    now() - INTERVAL '7 hours', now() - INTERVAL '7 hours'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO verdicts (id, tenant_id, snapshot_id, status, confidence, created_at, updated_at) VALUES
(
    '00000000-0000-4000-d000-000000000061',
    '00000000-0000-4000-b000-000000000021',
    '00000000-0000-4000-d000-000000000051',
    'RELIABLE', 'FRESH', now() - INTERVAL '6 hours', now() - INTERVAL '6 hours'
),
(
    '00000000-0000-4000-d000-000000000062',
    '00000000-0000-4000-b000-000000000021',
    '00000000-0000-4000-d000-000000000052',
    'RELIABLE', 'FRESH', now() - INTERVAL '7 hours', now() - INTERVAL '7 hours'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO watchlist_entries (
    id, tenant_id, tax_number, label, company_name,
    last_verdict_status, last_checked_at, latest_sha256_hash, previous_verdict_status,
    created_at, updated_at
) VALUES
(
    '00000000-0000-4000-d000-000000000071',
    '00000000-0000-4000-b000-000000000021',
    '66778899142', 'Faanyag szállító', 'Fa Alapanyag Kft.',
    'RELIABLE', now() - INTERVAL '6 hours',
    'c4d3e2f1a6b5c4d3e2f1a6b5c4d3e2f1a6b5c4d3e2f1a6b5c4d3e2f1a6b5c4d3', NULL,
    now() - INTERVAL '50 days', now() - INTERVAL '6 hours'
),
(
    '00000000-0000-4000-d000-000000000072',
    '00000000-0000-4000-b000-000000000021',
    '55443322113', 'Vegyi anyag', 'Vegyi Anyag Bt.',
    'RELIABLE', now() - INTERVAL '7 hours',
    'd3e2f1a6b5c4d3e2f1a6b5c4d3e2f1a6b5c4d3e2f1a6b5c4d3e2f1a6b5c4d3e2', NULL,
    now() - INTERVAL '40 days', now() - INTERVAL '7 hours'
)
ON CONFLICT (id) DO NOTHING;

-- EPR templates for Prémium Bútor: wood packaging, cardboard transport box
INSERT INTO epr_material_templates (
    id, tenant_id, name, base_weight_grams, kf_code, verified, recurring, created_at, updated_at
) VALUES
(
    '00000000-0000-4000-d000-000000000081',
    '00000000-0000-4000-b000-000000000021',
    'Farekesz (szállítási)', 2500.0, '11030301', true, true,
    now() - INTERVAL '18 days', now() - INTERVAL '18 days'
),
(
    '00000000-0000-4000-d000-000000000082',
    '00000000-0000-4000-b000-000000000021',
    'Hullámpapír szállítódoboz (nagy)', 850.0, '11010301', true, true,
    now() - INTERVAL '18 days', now() - INTERVAL '18 days'
)
ON CONFLICT (id) DO NOTHING;

-- EPR calculation for Prémium Bútor Q1 2026
-- 11030301 (wood, transport) → 10.22 HUF/kg; 11010301 (paper, transport) → 20.44 HUF/kg
INSERT INTO epr_calculations (
    id, tenant_id, config_version, template_id,
    traversal_path, material_classification, kf_code,
    fee_rate, quantity, total_weight_grams, fee_amount, currency,
    confidence, override_kf_code, override_reason, created_at
) VALUES
(
    '00000000-0000-4000-d000-000000000090',
    '00000000-0000-4000-b000-000000000021',
    1, '00000000-0000-4000-d000-000000000081',
    '[{"step":"product_stream","selected":"11"},{"step":"material","selected":"03"},{"step":"group","selected":"03"},{"step":"subgroup","selected":"01"}]'::jsonb,
    'Nem kötelezően visszaváltási díjas egyszer használatos csomagolás / Fa / Szállítási',
    '11030301', 10.22, 0, 0, 0, 'HUF',
    'HIGH', NULL, NULL, now() - INTERVAL '18 days'
),
(
    '00000000-0000-4000-d000-000000000091',
    '00000000-0000-4000-b000-000000000021',
    1, '00000000-0000-4000-d000-000000000082',
    '[{"step":"product_stream","selected":"11"},{"step":"material","selected":"01"},{"step":"group","selected":"03"},{"step":"subgroup","selected":"01"}]'::jsonb,
    'Nem kötelezően visszaváltási díjas egyszer használatos csomagolás / Papír és karton / Szállítási',
    '11010301', 20.44, 5000, 4250000.0, 86870, 'HUF',
    'HIGH', NULL, NULL, now() - INTERVAL '7 days'
)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- SECTION 12: Client 3 — TechStart Kft. (ALAP)
-- =============================================================================
-- Snapshot + verdict for TechStart's 1 watchlist partner (AT_RISK)
INSERT INTO company_snapshots (id, tenant_id, tax_number, snapshot_data, created_at, updated_at) VALUES
(
    '00000000-0000-4000-d000-000000000101',
    '00000000-0000-4000-b000-000000000022',
    '33221100142',
    '{"available":true,"companyName":"Problémás Partner Kft.","registrationNumber":"01-09-332211","taxNumberStatus":"ACTIVE","hasPublicDebt":true,"hasInsolvencyProceedings":false,"debtAmount":890000,"debtCurrency":"HUF","status":"ACTIVE"}'::jsonb,
    now() - INTERVAL '1 day', now() - INTERVAL '1 day'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO verdicts (id, tenant_id, snapshot_id, status, confidence, created_at, updated_at) VALUES
(
    '00000000-0000-4000-d000-000000000111',
    '00000000-0000-4000-b000-000000000022',
    '00000000-0000-4000-d000-000000000101',
    'AT_RISK', 'STALE', now() - INTERVAL '1 day', now() - INTERVAL '1 day'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO watchlist_entries (
    id, tenant_id, tax_number, label, company_name,
    last_verdict_status, last_checked_at, latest_sha256_hash, previous_verdict_status,
    created_at, updated_at
) VALUES
(
    '00000000-0000-4000-d000-000000000121',
    '00000000-0000-4000-b000-000000000022',
    '33221100142', 'Kulcsügyfél', 'Problémás Partner Kft.',
    'AT_RISK', now() - INTERVAL '1 day',
    'e2f1a6b5c4d3e2f1a6b5c4d3e2f1a6b5c4d3e2f1a6b5c4d3e2f1a6b5c4d3e2f1', NULL,
    now() - INTERVAL '10 days', now() - INTERVAL '1 day'
)
ON CONFLICT (id) DO NOTHING;

-- 1 unverified EPR template (TechStart just started, hasn't classified yet)
INSERT INTO epr_material_templates (
    id, tenant_id, name, base_weight_grams, kf_code, verified, recurring, created_at, updated_at
) VALUES
(
    '00000000-0000-4000-d000-000000000131',
    '00000000-0000-4000-b000-000000000022',
    'Szoftveres eszköz doboza', 120.0, NULL, false, false,
    now() - INTERVAL '5 days', now() - INTERVAL '5 days'
)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- SECTION: NAV Tenant Credentials (for EPR invoice auto-fill)
-- =============================================================================
-- Demo SME tenant — tax_number matches DemoInvoiceFixtures (35 invoices)
INSERT INTO nav_tenant_credentials (
    id, tenant_id, login_encrypted, password_hash, signing_key_enc, exchange_key_enc, tax_number
) VALUES (
    '00000000-0000-4000-e000-000000000001',
    '00000000-0000-4000-b000-000000000001',
    'demo_encrypted_login', 'demo_password_hash_sha512',
    'demo_encrypted_signing_key', 'demo_encrypted_exchange_key',
    '12345678'
)
ON CONFLICT (tenant_id) DO NOTHING;

-- Accountant client 1: Zöld Élelmiszer Kft. — 28 construction invoices
INSERT INTO nav_tenant_credentials (
    id, tenant_id, login_encrypted, password_hash, signing_key_enc, exchange_key_enc, tax_number
) VALUES (
    '00000000-0000-4000-e000-000000000002',
    '00000000-0000-4000-b000-000000000020',
    'demo_encrypted_login', 'demo_password_hash_sha512',
    'demo_encrypted_signing_key', 'demo_encrypted_exchange_key',
    '99887766'
)
ON CONFLICT (tenant_id) DO NOTHING;

-- Accountant client 2: Prémium Bútor Zrt. — 42 manufacturing invoices
INSERT INTO nav_tenant_credentials (
    id, tenant_id, login_encrypted, password_hash, signing_key_enc, exchange_key_enc, tax_number
) VALUES (
    '00000000-0000-4000-e000-000000000003',
    '00000000-0000-4000-b000-000000000021',
    'demo_encrypted_login', 'demo_password_hash_sha512',
    'demo_encrypted_signing_key', 'demo_encrypted_exchange_key',
    '55667788'
)
ON CONFLICT (tenant_id) DO NOTHING;

-- =============================================================================
-- SECTION 13: Producer Profiles (Story 9.4 — OKIRkapu XML identity block)
-- =============================================================================
-- One profile per PRO_EPR tenant. Complete legal/address/contact/KSH/OKIR fields
-- so ProducerProfileService.get() returns without 412 PRECONDITION_FAILED and the
-- OKIRkapu XML export marshaller can populate every KG:KGYF-NÉ identity element.
INSERT INTO producer_profiles (
    id, tenant_id, legal_name,
    address_country_code, address_postal_code, address_city, address_street_name, address_street_type, address_house_number,
    ksh_statistical_number, company_registration_number,
    contact_name, contact_title, contact_country_code, contact_postal_code, contact_city, contact_street_name,
    contact_phone, contact_email,
    okir_client_id,
    is_manufacturer, is_individual_performer, is_subcontractor, is_concessionaire,
    created_at, updated_at
) VALUES
-- demo@riskguard.hu — Bemutató Kereskedelmi Kft.
(
    '00000000-0000-4000-f000-000000000001',
    '00000000-0000-4000-b000-000000000001',
    'Bemutató Kereskedelmi Kft.',
    'HU', '1062', 'Budapest', 'Andrássy', 'út', '42',
    '12345678-4690-113-01', '01-09-123456',
    'Demo Felhasználó', 'Ügyvezető', 'HU', '1062', 'Budapest', 'Andrássy út 42.',
    '+36-1-555-0101', 'demo@riskguard.hu',
    1234567,
    true, true, false, false,
    now() - INTERVAL '45 days', now() - INTERVAL '5 days'
),
-- Zöld Élelmiszer Kft. (accountant client 1)
(
    '00000000-0000-4000-f000-000000000002',
    '00000000-0000-4000-b000-000000000020',
    'Zöld Élelmiszer Kft.',
    'HU', '2000', 'Szentendre', 'Fő', 'tér', '7',
    '99887766-1032-113-13', '13-09-556677',
    'Kiss Edit', 'Pénzügyi vezető', 'HU', '2000', 'Szentendre', 'Fő tér 7.',
    '+36-26-555-0120', 'penzugy@zoldelelmiszer.hu',
    2345678,
    true, true, false, false,
    now() - INTERVAL '90 days', now() - INTERVAL '10 days'
),
-- Prémium Bútor Zrt. (accountant client 2)
(
    '00000000-0000-4000-f000-000000000003',
    '00000000-0000-4000-b000-000000000021',
    'Prémium Bútor Zrt.',
    'HU', '6000', 'Kecskemét', 'Ipari', 'út', '15',
    '55667788-3101-114-03', '03-10-998877',
    'Nagy Tamás', 'Logisztikai igazgató', 'HU', '6000', 'Kecskemét', 'Ipari út 15.',
    '+36-76-555-0133', 'logisztika@premiumbutor.hu',
    3456789,
    true, false, true, false,
    now() - INTERVAL '59 days', now() - INTERVAL '3 days'
)
ON CONFLICT (tenant_id) DO NOTHING;

-- =============================================================================
-- SECTION 14: Product Registry — Bemutató Kereskedelmi Kft. (tax 12345678)
-- =============================================================================
-- Products + components matching DemoInvoiceFixtures VTSZ codes so the OKIRkapu
-- XML export pipeline (RegistryLookupService.findByVtszOrArticleNumber) can
-- resolve invoice lines to REGISTRY_MATCH with weight contributions.
INSERT INTO products (id, tenant_id, article_number, name, vtsz, primary_unit, status, created_at, updated_at)
VALUES
(
    '00000000-0000-4000-f000-000000009001',
    '00000000-0000-4000-b000-000000000001',
    'CSA-M6X30', 'Csavar M6x30 (100db)', '73181500', 'pcs', 'ACTIVE',
    now() - INTERVAL '30 days', now() - INTERVAL '30 days'
),
(
    '00000000-0000-4000-f000-000000009002',
    '00000000-0000-4000-b000-000000000001',
    'PET-05L', 'PET palack 0,5L', '39233000', 'pcs', 'ACTIVE',
    now() - INTERVAL '30 days', now() - INTERVAL '30 days'
),
(
    '00000000-0000-4000-f000-000000009003',
    '00000000-0000-4000-b000-000000000001',
    'KDB-403020', 'Kartondoboz 40x30x20', '48191000', 'pcs', 'ACTIVE',
    now() - INTERVAL '30 days', now() - INTERVAL '30 days'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_packaging_components (
    id, product_id, material_description, kf_code,
    weight_per_unit_kg, items_per_parent, component_order,
    created_at, updated_at
) VALUES
-- Csavar M6x30: steel screw packaging → KF 91010101, 0.006 kg/unit (6g steel per 1 screw)
(
    '00000000-0000-4000-f000-00000000a001',
    '00000000-0000-4000-f000-000000009001',
    'Acél kötőelem', '91010101',
    0.006000, 1, 1,
    now() - INTERVAL '30 days', now() - INTERVAL '30 days'
),
-- PET palack: PET plastic → KF 11020101, 0.025 kg/unit (25g per bottle)
(
    '00000000-0000-4000-f000-00000000a002',
    '00000000-0000-4000-f000-000000009002',
    'PET palack csomagolás', '11020101',
    0.025000, 1, 1,
    now() - INTERVAL '30 days', now() - INTERVAL '30 days'
),
-- Kartondoboz: paper/cardboard → KF 11010101, 0.350 kg/unit (350g per box)
(
    '00000000-0000-4000-f000-00000000a003',
    '00000000-0000-4000-f000-000000009003',
    'Karton csomagolás', '11010101',
    0.350000, 1, 1,
    now() - INTERVAL '30 days', now() - INTERVAL '30 days'
)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- SECTION 15: Product Registry — Zöld Élelmiszer Kft. (tax 99887766)
-- =============================================================================
-- DemoInvoiceFixtures VTSZ: 72142000 (rebar), 25232900 (cement)
INSERT INTO products (id, tenant_id, article_number, name, vtsz, primary_unit, status, created_at, updated_at)
VALUES
(
    '00000000-0000-4000-f000-000000009011',
    '00000000-0000-4000-b000-000000000020',
    'BA-12-B500B', 'Betonacél Ø12 B500B (6m)', '72142000', 'pcs', 'ACTIVE',
    now() - INTERVAL '25 days', now() - INTERVAL '25 days'
),
(
    '00000000-0000-4000-f000-000000009012',
    '00000000-0000-4000-b000-000000000020',
    'CEM-II-25KG', 'Cement CEM II/B-M 32,5 R (25kg)', '25232900', 'pcs', 'ACTIVE',
    now() - INTERVAL '25 days', now() - INTERVAL '25 days'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_packaging_components (
    id, product_id, material_description, kf_code,
    weight_per_unit_kg, items_per_parent, component_order,
    created_at, updated_at
) VALUES
-- Betonacél: steel bar → KF 91010101, 8.88 kg/unit (6m rebar ~8.88kg)
(
    '00000000-0000-4000-f000-00000000a011',
    '00000000-0000-4000-f000-000000009011',
    'Acél rúd/profil', '91010101',
    8.880000, 1, 1,
    now() - INTERVAL '25 days', now() - INTERVAL '25 days'
),
-- Cement: cement bag packaging → KF 91010101, 0.050 kg/unit (50g paper sack per 25kg bag)
(
    '00000000-0000-4000-f000-00000000a012',
    '00000000-0000-4000-f000-000000009012',
    'Cement zsák csomagolás', '91010101',
    0.050000, 1, 1,
    now() - INTERVAL '25 days', now() - INTERVAL '25 days'
)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- SECTION 16: Product Registry — Prémium Bútor Zrt. (tax 55667788)
-- =============================================================================
-- DemoInvoiceFixtures VTSZ: 19059090 (bread), 11010015 (flour)
INSERT INTO products (id, tenant_id, article_number, name, vtsz, primary_unit, status, created_at, updated_at)
VALUES
(
    '00000000-0000-4000-f000-000000009021',
    '00000000-0000-4000-b000-000000000021',
    'KEN-F-500G', 'Kenyér (fehér, 500g)', '19059090', 'pcs', 'ACTIVE',
    now() - INTERVAL '20 days', now() - INTERVAL '20 days'
),
(
    '00000000-0000-4000-f000-000000009022',
    '00000000-0000-4000-b000-000000000021',
    'LISZT-BL55-1KG', 'Búzaliszt BL-55 (1kg)', '11010015', 'pcs', 'ACTIVE',
    now() - INTERVAL '20 days', now() - INTERVAL '20 days'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_packaging_components (
    id, product_id, material_description, kf_code,
    weight_per_unit_kg, items_per_parent, component_order,
    created_at, updated_at
) VALUES
-- Kenyér: bakery packaging (paper/plastic film) → KF 61010101, 0.008 kg/unit
(
    '00000000-0000-4000-f000-00000000a021',
    '00000000-0000-4000-f000-000000009021',
    'Pékárú csomagolás (PE fólia)', '61010101',
    0.008000, 1, 1,
    now() - INTERVAL '20 days', now() - INTERVAL '20 days'
),
-- Búzaliszt: flour bag packaging → KF 61010101, 0.015 kg/unit (15g paper bag per 1kg flour)
(
    '00000000-0000-4000-f000-00000000a022',
    '00000000-0000-4000-f000-000000009022',
    'Liszt zsák csomagolás (papír)', '61010101',
    0.015000, 1, 1,
    now() - INTERVAL '20 days', now() - INTERVAL '20 days'
)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- SECTION 18: OKIRkapu XML Exports (Story 9.4 — filing history)
-- =============================================================================
-- Adds OKIRKAPU_XML-format epr_exports for each PRO_EPR tenant so the filing history
-- page shows multi-format history (CSV from Section 7 + XML below).
-- Relies on V20260416_001 extending the export_format_type enum to include OKIRKAPU_XML.
-- period_start / period_end cover Q1 of the previous year — matching demo calculations.
INSERT INTO epr_exports (
    id, tenant_id, calculation_id, config_version, export_format, file_hash,
    period_start, period_end, exported_at
) VALUES
-- Bemutató Kereskedelmi — XML for Q1
(
    '00000000-0000-4000-f000-000000007001',
    '00000000-0000-4000-b000-000000000001',
    '00000000-0000-4000-c000-000000000041',
    1, 'OKIRKAPU_XML',
    'e1d2c3b4a5f6e1d2c3b4a5f6e1d2c3b4a5f6e1d2c3b4a5f6e1d2c3b4a5f6e1d2',
    date_trunc('quarter', now() - INTERVAL '3 months')::date,
    (date_trunc('quarter', now()) - INTERVAL '1 day')::date,
    now() - INTERVAL '5 days'
),
-- Zöld Élelmiszer — XML for Q1
(
    '00000000-0000-4000-f000-000000007002',
    '00000000-0000-4000-b000-000000000020',
    '00000000-0000-4000-d000-000000000041',
    1, 'OKIRKAPU_XML',
    'a9b8c7d6e5f4a9b8c7d6e5f4a9b8c7d6e5f4a9b8c7d6e5f4a9b8c7d6e5f4a9b8',
    date_trunc('quarter', now() - INTERVAL '3 months')::date,
    (date_trunc('quarter', now()) - INTERVAL '1 day')::date,
    now() - INTERVAL '6 days'
),
-- Prémium Bútor — XML for Q1
(
    '00000000-0000-4000-f000-000000007003',
    '00000000-0000-4000-b000-000000000021',
    '00000000-0000-4000-d000-000000000091',
    1, 'OKIRKAPU_XML',
    'b8c7d6e5f4a3b8c7d6e5f4a3b8c7d6e5f4a3b8c7d6e5f4a3b8c7d6e5f4a3b8c7',
    date_trunc('quarter', now() - INTERVAL '3 months')::date,
    (date_trunc('quarter', now()) - INTERVAL '1 day')::date,
    now() - INTERVAL '7 days'
)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- SECTION 19: VTSZ-aligned EPR templates for Demo Felhasználó (Pre-fill match)
-- =============================================================================
-- Templates whose `name` matches the materialName_hu from V20260403_001 vtszMappings —
-- this is what EprService.autoFillFromInvoices() uses for the "template matched" badge.
-- Without these, Bemutató Kereskedelmi's invoice lines (VTSZ 39233000, 48191000, 73181500)
-- would return suggestions with `hasExistingTemplate=false` for every row.
INSERT INTO epr_material_templates (
    id, tenant_id, name, base_weight_grams, kf_code, verified, recurring, created_at, updated_at
) VALUES
(
    '00000000-0000-4000-f000-000000008001',
    '00000000-0000-4000-b000-000000000001',
    'PET csomagolás', 25.0, '11020101', true, true,
    now() - INTERVAL '40 days', now() - INTERVAL '40 days'
),
(
    '00000000-0000-4000-f000-000000008002',
    '00000000-0000-4000-b000-000000000001',
    'Karton csomagolás', 350.0, '11010101', true, true,
    now() - INTERVAL '38 days', now() - INTERVAL '38 days'
),
(
    '00000000-0000-4000-f000-000000008003',
    '00000000-0000-4000-b000-000000000001',
    'Acél csavar/kötőelem', 600.0, '91010101', true, true,
    now() - INTERVAL '35 days', now() - INTERVAL '35 days'
)
ON CONFLICT (id) DO NOTHING;

-- SECTION 19b: epr_calculations for VTSZ-aligned templates (needed for feeRate)
-- Without these rows the filing page filters out SECTION 19 templates because feeRate=null,
-- which silently breaks the invoice auto-fill → filing-table flow.
--   11020101 → packaging_non_deposit / plastic (1102) → 42.89 HUF/kg
--   11010101 → packaging_non_deposit / paper   (1101) → 20.44 HUF/kg
--   91010101 → other_plastic_chemical           (9101) → 42.89 HUF/kg
INSERT INTO epr_calculations (
    id, tenant_id, config_version, template_id,
    traversal_path, material_classification, kf_code,
    fee_rate, quantity, total_weight_grams, fee_amount, currency,
    confidence, override_kf_code, override_reason, created_at
) VALUES
(
    '00000000-0000-4000-f000-000000008011',
    '00000000-0000-4000-b000-000000000001',
    1,
    '00000000-0000-4000-f000-000000008001',
    '[{"step":"product_stream","selected":"11"},{"step":"material","selected":"02"},{"step":"group","selected":"01"},{"step":"subgroup","selected":"01"}]'::jsonb,
    'Nem kötelezően visszaváltási díjas egyszer használatos csomagolás / Műanyag / Fogyasztói',
    '11020101',
    42.89, 4800, 120000.0, 5147, 'HUF',
    'HIGH', NULL, NULL,
    now() - INTERVAL '39 days'
),
(
    '00000000-0000-4000-f000-000000008012',
    '00000000-0000-4000-b000-000000000001',
    1,
    '00000000-0000-4000-f000-000000008002',
    '[{"step":"product_stream","selected":"11"},{"step":"material","selected":"01"},{"step":"group","selected":"01"},{"step":"subgroup","selected":"01"}]'::jsonb,
    'Nem kötelezően visszaváltási díjas egyszer használatos csomagolás / Papír és karton / Fogyasztói',
    '11010101',
    20.44, 500, 175000.0, 3577, 'HUF',
    'HIGH', NULL, NULL,
    now() - INTERVAL '37 days'
),
(
    '00000000-0000-4000-f000-000000008013',
    '00000000-0000-4000-b000-000000000001',
    1,
    '00000000-0000-4000-f000-000000008003',
    '[{"step":"product_stream","selected":"91"},{"step":"material","selected":"01"},{"step":"group","selected":"01"},{"step":"subgroup","selected":"01"}]'::jsonb,
    'Egyéb műanyag és vegyipari termék / Egyéb műanyag termék',
    '91010101',
    42.89, 1200, 720000.0, 30881, 'HUF',
    'HIGH', NULL, NULL,
    now() - INTERVAL '34 days'
)
ON CONFLICT (id) DO NOTHING;
