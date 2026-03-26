-- Story 4.3: Manual Override & Confidence Score
-- Add confidence score and manual override columns to epr_calculations

ALTER TABLE epr_calculations
    ADD COLUMN confidence       VARCHAR(10)  NOT NULL DEFAULT 'HIGH',
    ADD COLUMN override_kf_code VARCHAR(8),
    ADD COLUMN override_reason  TEXT;

-- Add CHECK constraint for valid confidence values
ALTER TABLE epr_calculations
    ADD CONSTRAINT chk_epr_calculations_confidence
    CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW'));

COMMENT ON COLUMN epr_calculations.confidence IS 'Wizard confidence in the KF-code mapping: HIGH, MEDIUM, LOW';
COMMENT ON COLUMN epr_calculations.override_kf_code IS 'User-selected override KF-code (NULL = no override, original kf_code applies)';
COMMENT ON COLUMN epr_calculations.override_reason IS 'Free-text reason for manual override (NULL if no override)';
