package hu.riskguard.core.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HashUtil}.
 * Validates SHA-256 output format, determinism, order sensitivity, multi-part concatenation,
 * null-safety, and empty-string handling — per Story 2.5 AC #6.
 */
class HashUtilTest {

    @Test
    void sha256ShouldReturnExactly64LowercaseHexChars() {
        // Given — any valid single-part input
        String result = HashUtil.sha256("hello world");

        // Then — result is exactly 64 lowercase hex chars (SHA-256 output is 256 bits = 32 bytes = 64 hex chars)
        assertThat(result).hasSize(64);
        assertThat(result).matches("[0-9a-f]{64}");
    }

    @Test
    void sha256ShouldBeDeterministic() {
        // Given — same inputs called twice
        String first  = HashUtil.sha256("snapshot-data", "RELIABLE");
        String second = HashUtil.sha256("snapshot-data", "RELIABLE");

        // Then — identical outputs (determinism)
        assertThat(first).isEqualTo(second);
    }

    @Test
    void sha256ShouldBeOrderSensitive() {
        // Given — same parts but in different order
        String hashAB = HashUtil.sha256("a", "b");
        String hashBA = HashUtil.sha256("b", "a");

        // Then — different hashes (order matters for legal proof determinism)
        assertThat(hashAB).isNotEqualTo(hashBA);
    }

    @Test
    void sha256MultiPartShouldNotEqualConcatenatedSinglePart() {
        // Given — "ab"+"cd" as two parts vs "abcd" as one part
        // With the null-byte separator fix, sha256("ab","cd") != sha256("abcd")
        // because each part is followed by 0x00, preventing boundary-collision attacks.
        String multiPart  = HashUtil.sha256("ab", "cd");
        String singlePart = HashUtil.sha256("abcd");

        // Then — DIFFERENT hashes (separator prevents part-boundary collisions)
        assertThat(multiPart).isNotEqualTo(singlePart);
    }

    @Test
    void sha256ShouldThrowIllegalArgumentExceptionForNullPart() {
        // Given — null part in inputs
        // Then — IAE thrown with message containing "null" (not NPE, explicit contract)
        assertThatThrownBy(() -> HashUtil.sha256(null, "b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test
    void sha256ShouldThrowIllegalArgumentExceptionWhenAnyPartIsNull() {
        // Given — null in middle of parts
        assertThatThrownBy(() -> HashUtil.sha256("a", null, "c"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test
    void sha256ShouldAcceptEmptyStringAndReturnValid64CharHex() {
        // Given — empty string with separator: SHA-256 of [0x00] (one null separator byte after the empty part)
        // Pre-computed: SHA-256(0x00) = 6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d
        String result = HashUtil.sha256("");

        // Then — valid 64-char hex
        assertThat(result).hasSize(64);
        assertThat(result).matches("[0-9a-f]{64}");
        // Verify against known value: SHA-256 of a single null byte
        assertThat(result).isEqualTo("6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d");
    }

    @Test
    void sha256FourPartHashShouldMatchExpectedLegalProofFormat() {
        // Given — the four parts used for audit trail (Story 2.5 AC #1)
        String snapshotData   = "{\"demo\":{\"available\":true}}";
        String verdictStatus  = "RELIABLE";
        String verdictConf    = "FRESH";
        String disclaimerText = "This search result is provided for informational purposes only.";

        // When
        String hash = HashUtil.sha256(snapshotData, verdictStatus, verdictConf, disclaimerText);

        // Then — valid 64-char hex; same inputs always produce same hash
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(hash).isEqualTo(HashUtil.sha256(snapshotData, verdictStatus, verdictConf, disclaimerText));
    }
}
