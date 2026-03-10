package hu.riskguard.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates a Hungarian tax number (adószám).
 * Accepts both 8-digit (company tax ID) and 11-digit (full tax identification) formats.
 * Strips hyphens and whitespace before validation.
 */
@Documented
@Constraint(validatedBy = HungarianTaxNumberValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface HungarianTaxNumber {
    String message() default "Invalid Hungarian tax number";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
