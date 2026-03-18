-- Story 3.3: Add CHECK constraint on tenants.tier to enforce valid tier values.
-- All existing tenants have tier = 'ALAP' (the default), so this constraint applies cleanly.
ALTER TABLE tenants ADD CONSTRAINT chk_tenants_tier CHECK (tier IN ('ALAP', 'PRO', 'PRO_EPR'));
