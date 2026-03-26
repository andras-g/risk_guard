package hu.riskguard.epr.api.dto;

import hu.riskguard.epr.domain.DagEngine;

import java.util.List;

/**
 * Response for {@code GET /wizard/start} — root-level product stream options.
 *
 * @param configVersion active config version
 * @param level         current hierarchy level ("product_stream")
 * @param options       available product stream options
 */
public record WizardStartResponse(
        int configVersion,
        String level,
        List<WizardOption> options
) {

    public static WizardStartResponse from(int configVersion, List<DagEngine.WizardOption> domainOptions) {
        return new WizardStartResponse(
                configVersion,
                "product_stream",
                domainOptions.stream().map(WizardOption::from).toList()
        );
    }
}
