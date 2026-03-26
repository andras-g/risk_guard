package hu.riskguard.epr.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for {@code POST /wizard/resolve} — resolve the final KF-code from a complete traversal.
 *
 * <p>Separate from {@link WizardStepRequest} because resolve does not require a {@code selection}
 * field — the complete traversal path is sufficient.
 *
 * @param configVersion config version (for reproducibility)
 * @param traversalPath complete 4-level traversal path
 */
public record WizardResolveRequest(
        int configVersion,
        @NotNull List<@Valid WizardSelection> traversalPath
) {}
