-- Add missing foreign key reference for guest_sessions
-- Note: guest_sessions use synthetic tenant IDs that might not exist in the tenants table if they are pure guests.
-- However, according to the review, a FK is expected. 
-- For pure guests, we will eventually need a 'Guest Root' tenant record or handle this differently.
-- For now, we establish the constraint.

ALTER TABLE guest_sessions
ADD CONSTRAINT fk_guest_sessions_tenants
FOREIGN KEY (tenant_id) REFERENCES tenants(id);
