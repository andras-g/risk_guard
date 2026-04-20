-- Story 10.4: Remove Story 9.2's candidate-triage table.
-- The new end-to-end bootstrap (InvoiceDrivenRegistryBootstrapService) writes directly into
-- products + product_packaging_components; the triage queue is no longer needed.
--
-- Pre-drop sanity: abort (RAISE EXCEPTION) if any APPROVED rows exist. All known envs have
-- zero APPROVED rows — a non-zero count means someone used the triage UI in an unexpected
-- environment, and silently dropping those rows would lose approved bootstrap results.
-- Operators who accept the loss can set flyway.placeholders.force_drop_bootstrap_candidates
-- (out-of-scope for this story) or run a manual DELETE first.
--
-- Idempotency: DROP TABLE IF EXISTS + IF EXISTS guards throughout.

DO $$
DECLARE
    approved_count INT;
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'registry_bootstrap_candidates'
    ) THEN
        SELECT COUNT(*) INTO approved_count
        FROM registry_bootstrap_candidates
        WHERE status = 'APPROVED';

        IF approved_count > 0 THEN
            RAISE EXCEPTION 'registry_bootstrap_candidates has % APPROVED row(s) — drop aborted to prevent data loss. Review and DELETE manually before re-running this migration.', approved_count;
        ELSE
            RAISE NOTICE 'registry_bootstrap_candidates APPROVED count = 0 (expected) — safe to drop';
        END IF;
    END IF;
END
$$;

DROP TABLE IF EXISTS registry_bootstrap_candidates CASCADE;

-- -- ROLLBACK (re-creates the table as per V20260414_002):
-- CREATE TABLE registry_bootstrap_candidates (
--     id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
--     tenant_id               UUID            NOT NULL REFERENCES tenants(id),
--     product_name            VARCHAR(512)    NOT NULL,
--     vtsz                    VARCHAR(16)     NULL,
--     frequency               INT             NOT NULL DEFAULT 1,
--     total_quantity          NUMERIC(14,3)   NOT NULL DEFAULT 0,
--     unit_of_measure         VARCHAR(16)     NULL,
--     status                  VARCHAR(48)     NOT NULL DEFAULT 'PENDING'
--                                             CHECK (status IN (
--                                                 'PENDING',
--                                                 'APPROVED',
--                                                 'REJECTED_NOT_OWN_PACKAGING',
--                                                 'NEEDS_MANUAL_ENTRY'
--                                             )),
--     suggested_kf_code       VARCHAR(16)     NULL,
--     suggested_components    JSONB           NULL,
--     classification_strategy VARCHAR(32)     NULL,
--     classification_confidence VARCHAR(16)   NULL,
--     resulting_product_id    UUID            NULL REFERENCES products(id) ON DELETE SET NULL,
--     created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
--     updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
-- );
-- CREATE INDEX idx_rbc_tenant_status       ON registry_bootstrap_candidates (tenant_id, status);
-- CREATE INDEX idx_rbc_tenant_product_name ON registry_bootstrap_candidates (tenant_id, product_name);
-- CREATE UNIQUE INDEX uq_rbc_tenant_product_vtsz
--     ON registry_bootstrap_candidates (tenant_id, product_name, COALESCE(vtsz, ''));
-- CREATE OR REPLACE FUNCTION set_updated_at()
-- RETURNS TRIGGER AS $$
-- BEGIN
--     NEW.updated_at = now();
--     RETURN NEW;
-- END;
-- $$ LANGUAGE plpgsql;
-- CREATE TRIGGER trg_rbc_updated_at
--     BEFORE UPDATE ON registry_bootstrap_candidates
--     FOR EACH ROW EXECUTE FUNCTION set_updated_at();
