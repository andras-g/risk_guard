package hu.riskguard.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * Internationalisation configuration for the RiskGuard backend.
 *
 * <p>Resolves the request locale from the {@code Accept-Language} header sent by
 * the Nuxt frontend. The default locale is Hungarian ({@code hu}) with English
 * ({@code en}) as the only alternative.
 *
 * <p>Spring Boot auto-configures {@link org.springframework.context.MessageSource}
 * when {@code messages.properties} exists on the classpath, using the default
 * {@code spring.messages.basename=messages} setting.
 */
@Configuration
public class I18nConfig {

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.forLanguageTag("hu"));
        resolver.setSupportedLocales(List.of(
                Locale.forLanguageTag("hu"),
                Locale.ENGLISH
        ));
        return resolver;
    }
}
