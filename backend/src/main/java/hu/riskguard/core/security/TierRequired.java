package hu.riskguard.core.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method or class as requiring a minimum subscription tier.
 * Enforced by {@link TierGateInterceptor} at the Spring MVC layer.
 *
 * <p>Usage: {@code @TierRequired(Tier.PRO)} on a controller method or class.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TierRequired {
    Tier value();
}
