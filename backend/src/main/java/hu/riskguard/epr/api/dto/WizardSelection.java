package hu.riskguard.epr.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A user's selection at one level of the wizard traversal.
 *
 * @param level hierarchy level: "product_stream", "material_stream", "group", "subgroup"
 * @param code  two-digit code selected at this level
 * @param label localized label for display (breadcrumb)
 */
public record WizardSelection(
        @NotBlank String level,
        @NotBlank String code,
        String label
) {}
