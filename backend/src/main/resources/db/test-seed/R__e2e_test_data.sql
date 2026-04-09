-- E2E Test Data Seed (Repeatable Migration)
-- ============================================
-- Seeds a canonical test tenant and test user for Playwright E2E tests.
-- Uses deterministic UUIDs so the data is idempotent (ON CONFLICT DO NOTHING).
--
-- IMPORTANT: This migration only runs when the 'e2e' Spring profile is active
-- (application-e2e.yml adds classpath:db/test-seed to flyway.locations).
-- It is NOT applied in production — the default application.yml only includes
-- classpath:db/migration.
--
-- These fixed IDs are shared with the Playwright test fixtures (auth.setup.ts):
--   Tenant: e2e-test-tenant  → 00000000-0000-4000-a000-000000000001
--   User:   e2e@riskguard.hu → 00000000-0000-4000-a000-000000000002

INSERT INTO tenants (id, name, tier)
VALUES ('00000000-0000-4000-a000-000000000001', 'E2E Test Tenant', 'ALAP')
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, tenant_id, email, name, role, preferred_language, sso_provider, sso_subject)
VALUES (
    '00000000-0000-4000-a000-000000000002',
    '00000000-0000-4000-a000-000000000001',
    'e2e@riskguard.hu',
    'E2E Test User',
    'SME_ADMIN',
    'hu',
    'test',
    'e2e-test-subject'
)
ON CONFLICT (id) DO NOTHING;

-- PLATFORM_ADMIN test user (deterministic UUID a000-000000000003)
INSERT INTO users (id, tenant_id, email, name, role, preferred_language, sso_provider, sso_subject)
VALUES (
    '00000000-0000-4000-a000-000000000003',
    '00000000-0000-4000-a000-000000000001',
    'e2e-platform-admin@riskguard.hu',
    'E2E Platform Admin',
    'PLATFORM_ADMIN',
    'hu',
    'test',
    'e2e-platform-admin-subject'
)
ON CONFLICT (id) DO NOTHING;
