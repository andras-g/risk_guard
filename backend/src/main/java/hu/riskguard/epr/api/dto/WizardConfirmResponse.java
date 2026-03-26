package hu.riskguard.epr.api.dto;

import java.util.UUID;

/**
 * Response for {@code POST /wizard/confirm} — confirmation result.
 *
 * @param calculationId  the UUID of the persisted epr_calculations record
 * @param kfCode         the resolved 8-digit KF code
 * @param templateUpdated whether the linked template was updated (kf_code + verified)
 */
public record WizardConfirmResponse(
        UUID calculationId,
        String kfCode,
        boolean templateUpdated
) {

    public static WizardConfirmResponse from(UUID calculationId, String kfCode, boolean templateUpdated) {
        return new WizardConfirmResponse(calculationId, kfCode, templateUpdated);
    }
}
