package hu.riskguard.epr.api.dto;

import hu.riskguard.epr.domain.DagEngine;

import java.util.List;

/**
 * Response for {@code POST /wizard/step} — next level's options after a selection.
 *
 * @param configVersion active config version
 * @param currentLevel  the level just selected
 * @param nextLevel     the next hierarchy level to select (null if traversal complete)
 * @param options       available options for the next level
 * @param breadcrumb    full traversal path so far (for display)
 * @param autoSelect    if true, the single option was auto-selected — frontend should auto-advance
 */
public record WizardStepResponse(
        int configVersion,
        String currentLevel,
        String nextLevel,
        List<WizardOption> options,
        List<WizardSelection> breadcrumb,
        boolean autoSelect
) {

    public static WizardStepResponse from(int configVersion, String currentLevel, String nextLevel,
                                          DagEngine.WizardStepResult stepResult,
                                          List<WizardSelection> breadcrumb) {
        return new WizardStepResponse(
                configVersion,
                currentLevel,
                nextLevel,
                stepResult.options().stream().map(WizardOption::from).toList(),
                breadcrumb,
                stepResult.autoSelect()
        );
    }
}
