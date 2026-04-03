package hu.riskguard.datasource.internal.adapters.nav;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SignatureService}.
 * Test vector sourced from NAV Online Számla API v3 specification (ADR-6 example).
 *
 * <p>Input: requestId="TSTKFT1222564", timestamp="20171230182545" UTC, signingKey="ce-8f5e215119fa7dd621DLMRHRLH2S"
 * Concatenated: "TSTKFT122256420171230182545ce-8f5e215119fa7dd621DLMRHRLH2S"
 */
class SignatureServiceTest {

    private final SignatureService signatureService = new SignatureService();

    /**
     * Verifies that the SHA3-512 computation matches the known-good test vector
     * from the NAV Online Számla API v3 specification.
     */
    @Test
    void computeRequestSignature_navSpecTestVector_returnsCorrectHash() {
        // Given — NAV spec example values
        String requestId = "TSTKFT1222564";
        Instant timestamp = Instant.parse("2017-12-30T18:25:45Z"); // → "20171230182545"
        String signingKey = "ce-8f5e215119fa7dd621DLMRHRLH2S";

        // When
        String result = signatureService.computeRequestSignature(requestId, timestamp, signingKey);

        // Then — SHA3-512("TSTKFT122256420171230182545ce-8f5e215119fa7dd621DLMRHRLH2S").toUpperCase()
        assertThat(result).isEqualTo(
                "DD5E4039590FCB1599E94C9A2CB3BC7228450641185F351DC947C95AC927B9BA9" +
                "DBFB8D4EB676583B6B71F09B507B4E3105328B38B5A5D78BC625D4BE03CCF6F"
        );
    }

    @Test
    void computeRequestSignature_outputIsUppercase() {
        String result = signatureService.computeRequestSignature(
                "REQUESTID00000001",
                Instant.parse("2026-04-02T10:00:00Z"),
                "anySigningKey"
        );
        assertThat(result).isEqualTo(result.toUpperCase());
        assertThat(result).hasSize(128); // SHA3-512 = 64 bytes = 128 hex chars
    }

    @Test
    void computeRequestSignature_differentInputsProduceDifferentHashes() {
        String sig1 = signatureService.computeRequestSignature(
                "REQUEST01", Instant.parse("2026-01-01T00:00:00Z"), "key1"
        );
        String sig2 = signatureService.computeRequestSignature(
                "REQUEST02", Instant.parse("2026-01-01T00:00:00Z"), "key1"
        );
        assertThat(sig1).isNotEqualTo(sig2);
    }
}
