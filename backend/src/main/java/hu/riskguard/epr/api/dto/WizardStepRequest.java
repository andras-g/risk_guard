package hu.riskguard.epr.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for {@code POST /wizard/step} — advance the wizard by one level.
 *
 * @param configVersion config version (for reproducibility)
 * @param traversalPath previously selected options in order
 * @param selection     the new selection at the current level
 */
public record WizardStepRequest(
        int configVersion,
        @NotNull List<@Valid WizardSelection> traversalPath,
        @NotNull @Valid WizardSelection selection
) {}
