package hu.riskguard.core.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the locale (language/region) that an export component uses for output formatting.
 * Example: {@code @ExportLocale("hu")} signals that all generated output follows Hungarian
 * formatting conventions (e.g., MOHU CSV uses Hungarian column headers and comma as decimal separator).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExportLocale {
    /** BCP 47 language tag, e.g. "hu", "en". */
    String value() default "hu";
}
