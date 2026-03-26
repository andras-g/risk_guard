package hu.riskguard.epr.api.dto;

import hu.riskguard.epr.domain.DagEngine;

/**
 * A single option in a wizard step (e.g., a material type or usage context).
 *
 * @param code        two-digit hierarchy code (e.g., "01", "11")
 * @param label       localized display label
 * @param description optional description (nullable)
 */
public record WizardOption(String code, String label, String description) {

    public static WizardOption from(DagEngine.WizardOption domainOption) {
        return new WizardOption(domainOption.code(), domainOption.label(), domainOption.description());
    }
}
