-- Story 9.4: Producer Profile table for OKIRkapu XML export identity block.
-- Stores OKIR/OKIRKAPU data-reporter identity required by KG:KGYF-NÉ XSD.
-- One profile per tenant (UNIQUE constraint on tenant_id).

CREATE TABLE producer_profiles (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID        NOT NULL UNIQUE REFERENCES tenants(id),
    legal_name                  VARCHAR(255) NOT NULL,
    -- Registered address fields (SZEKHELY_* in XSD KG_KGYF_NE element)
    address_country_code        VARCHAR(3)  NOT NULL DEFAULT 'HU',
    address_city                VARCHAR(100) NULL,
    address_postal_code         VARCHAR(20)  NULL,
    address_street_name         VARCHAR(100) NULL,
    address_street_type         VARCHAR(20)  NULL,
    address_house_number        VARCHAR(20)  NULL,
    -- KSH statistical number (full 17-char: NNNNNNNN-TTTT-GGG-MM format)
    -- Decomposed at marshal time into ksh_torzsszam/teaor_kod/gazdforma_kod/megye_kod.
    ksh_statistical_number      CHAR(17)    NULL,
    -- Company registration number (format: XX-XX-XXXXXXXX)
    company_registration_number VARCHAR(16) NULL,
    -- Contact person (KAPCSTARTO_* fields in XSD)
    contact_name                VARCHAR(100) NULL,
    contact_title               VARCHAR(100) NULL,
    contact_country_code        VARCHAR(3)  NULL DEFAULT 'HU',
    contact_postal_code         VARCHAR(20)  NULL,
    contact_city                VARCHAR(100) NULL,
    contact_street_name         VARCHAR(100) NULL,
    contact_phone               VARCHAR(50)  NULL,
    contact_email               VARCHAR(100) NULL,
    -- OKIR client number (KUJ field in ADATCSOMAG header block)
    okir_client_id              INT         NULL,
    -- EPR role flags (stored for XSD GYARTO_10 / EGYENI_TELJESITO_10 etc.)
    is_manufacturer             BOOLEAN     NOT NULL DEFAULT true,
    is_individual_performer     BOOLEAN     NOT NULL DEFAULT true,
    is_subcontractor            BOOLEAN     NOT NULL DEFAULT false,
    is_concessionaire           BOOLEAN     NOT NULL DEFAULT false,
    -- Audit timestamps
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_producer_profiles_tenant ON producer_profiles(tenant_id);

-- updated_at auto-maintenance via trigger (set_updated_at() already exists from V20260414_001)
CREATE TRIGGER trg_producer_profiles_updated_at
    BEFORE UPDATE ON producer_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
