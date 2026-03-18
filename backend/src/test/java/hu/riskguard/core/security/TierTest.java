package hu.riskguard.core.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TierTest {

    @ParameterizedTest(name = "{0}.satisfies({1}) should be {2}")
    @CsvSource({
            "ALAP, ALAP, true",
            "ALAP, PRO, false",
            "ALAP, PRO_EPR, false",
            "PRO, ALAP, true",
            "PRO, PRO, true",
            "PRO, PRO_EPR, false",
            "PRO_EPR, ALAP, true",
            "PRO_EPR, PRO, true",
            "PRO_EPR, PRO_EPR, true"
    })
    void satisfiesReturnsCorrectResult(String current, String required, boolean expected) {
        Tier currentTier = Tier.valueOf(current);
        Tier requiredTier = Tier.valueOf(required);

        assertThat(currentTier.satisfies(requiredTier)).isEqualTo(expected);
    }

    @Test
    void valueOfResolvesAllTiers() {
        assertThat(Tier.valueOf("ALAP")).isEqualTo(Tier.ALAP);
        assertThat(Tier.valueOf("PRO")).isEqualTo(Tier.PRO);
        assertThat(Tier.valueOf("PRO_EPR")).isEqualTo(Tier.PRO_EPR);
    }

    @Test
    void valueOfRejectsInvalidTier() {
        assertThatThrownBy(() -> Tier.valueOf("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ordinalOrderMatchesHierarchy() {
        assertThat(Tier.ALAP.ordinal()).isLessThan(Tier.PRO.ordinal());
        assertThat(Tier.PRO.ordinal()).isLessThan(Tier.PRO_EPR.ordinal());
    }
}
