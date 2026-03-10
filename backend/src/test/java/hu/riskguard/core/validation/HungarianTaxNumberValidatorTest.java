package hu.riskguard.core.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HungarianTaxNumberValidatorTest {

    private HungarianTaxNumberValidator validator;

    @BeforeEach
    void setUp() {
        validator = new HungarianTaxNumberValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678", "1234-5678", "1234 5678"})
    void shouldAcceptValid8DigitTaxNumber(String taxNumber) {
        assertThat(validator.isValid(taxNumber, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678901", "1234-5678-901", "1234 5678 901"})
    void shouldAcceptValid11DigitTaxNumber(String taxNumber) {
        assertThat(validator.isValid(taxNumber, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "123456", "1"})
    void shouldRejectTooShortTaxNumber(String taxNumber) {
        assertThat(validator.isValid(taxNumber, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789012", "1234567890123"})
    void shouldRejectTooLongTaxNumber(String taxNumber) {
        assertThat(validator.isValid(taxNumber, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789", "1234567890"})
    void shouldReject9And10DigitNumbers(String taxNumber) {
        assertThat(validator.isValid(taxNumber, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcdefgh", "1234abcd", "12345678abc"})
    void shouldRejectNonNumericInput(String taxNumber) {
        assertThat(validator.isValid(taxNumber, null)).isFalse();
    }

    @Test
    void shouldRejectNull() {
        assertThat(validator.isValid(null, null)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void shouldRejectBlankAndEmpty(String taxNumber) {
        assertThat(validator.isValid(taxNumber, null)).isFalse();
    }
}
