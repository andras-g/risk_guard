package hu.riskguard.epr.api.dto;

/**
 * Request DTO for toggling the recurring flag on a material template.
 *
 * @param recurring the new recurring flag value
 */
public record RecurringToggleRequest(boolean recurring) {
}
