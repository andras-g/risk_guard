-- Add token-level usage tracking to ai_classifier_usage for accurate cost metering.
-- Replaces the flat 0.15 Ft/call estimate with actual input/output token counts.

ALTER TABLE ai_classifier_usage
    ADD COLUMN input_tokens  BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN output_tokens BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN ai_classifier_usage.input_tokens  IS 'Cumulative prompt tokens consumed this month (from Gemini usageMetadata)';
COMMENT ON COLUMN ai_classifier_usage.output_tokens IS 'Cumulative completion tokens consumed this month (from Gemini usageMetadata)';
