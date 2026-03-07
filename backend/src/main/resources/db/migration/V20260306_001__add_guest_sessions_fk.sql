-- Fix guest_sessions tenant isolation.
-- Guest sessions use synthetic tenant IDs (guest-{uuid}) that don't exist in the tenants table.
-- Instead of adding a FK constraint that would break guest creation, we drop the NOT NULL
-- on tenant_id for guest_sessions (since guests don't have real tenants) and leave the
-- column nullable without a FK. Authenticated guest_sessions that DO reference a real
-- tenant can still be validated at the application level.

-- Remove NOT NULL constraint on tenant_id for guest_sessions
-- (The original DDL had "tenant_id UUID NOT NULL" but guests use synthetic IDs)
ALTER TABLE guest_sessions ALTER COLUMN tenant_id DROP NOT NULL;

-- Add a comment explaining the design decision
COMMENT ON COLUMN guest_sessions.tenant_id IS 'Synthetic tenant ID for guest isolation. Not FK-constrained because guests use generated IDs that do not exist in the tenants table.';
