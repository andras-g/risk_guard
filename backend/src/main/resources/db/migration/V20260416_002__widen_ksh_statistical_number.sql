-- Story 9.4 follow-up: producer_profiles.ksh_statistical_number was CHAR(17), but both
-- the DTO regex (`^\d{8}-\d{4}-\d{3}-\d{2}$`) and the marshaller (splits on '-') require
-- the full 20-char dashed form (NNNNNNNN-TTTT-GGG-MM). No test ever inserted a non-null
-- value, so the size mismatch went undetected. Widening to VARCHAR(20) unblocks real
-- profile saves and lets demo data carry the full KSH string for XML export identity.
ALTER TABLE producer_profiles
    ALTER COLUMN ksh_statistical_number TYPE VARCHAR(20);
