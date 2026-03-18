package hu.riskguard.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for I18nConfig — verify MessageSource resolves keys
 * in both Hungarian and English locales, and that the LocaleResolver
 * defaults to Hungarian.
 */
@SpringBootTest
@ActiveProfiles("e2e")
class I18nConfigTest {

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private LocaleResolver localeResolver;

    @Test
    void messageSource_resolves_key_in_hungarian_locale() {
        Locale hu = Locale.forLanguageTag("hu");
        String msg = messageSource.getMessage("export.locale.notice", null, hu);
        assertThat(msg).contains("magyar");
    }

    @Test
    void messageSource_resolves_key_in_english_locale() {
        Locale en = Locale.ENGLISH;
        String msg = messageSource.getMessage("export.locale.notice", null, en);
        assertThat(msg).contains("Hungarian");
    }

    @Test
    void messageSource_resolves_parameterized_key() {
        Locale hu = Locale.forLanguageTag("hu");
        String msg = messageSource.getMessage("email.subject.statusChange", new Object[]{"TestCorp Kft."}, hu);
        assertThat(msg).contains("TestCorp Kft.");
    }

    @Test
    void localeResolver_is_AcceptHeaderLocaleResolver() {
        assertThat(localeResolver).isInstanceOf(
                org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver.class
        );
    }
}
