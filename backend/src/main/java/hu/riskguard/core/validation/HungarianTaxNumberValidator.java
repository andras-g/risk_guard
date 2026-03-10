package hu.riskguard.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validates Hungarian tax numbers.
 * Accepts:
 * - 8-digit format: adószám (company tax ID), e.g. "12345678"
 * - 11-digit format: adóazonosító jel (full tax identification with area code + check digit), e.g. "12345678901"
 * Strips hyphens and whitespace before matching.
 */
public class HungarianTaxNumberValidator implements ConstraintValidator<HungarianTaxNumber, String> {

    private static final Pattern PATTERN = Pattern.compile("^\\d{8}(\\d{3})?$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String cleaned = value.replaceAll("[\\s-]", "");
        return PATTERN.matcher(cleaned).matches();
    }
}
