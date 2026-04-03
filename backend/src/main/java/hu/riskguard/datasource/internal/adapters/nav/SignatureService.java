package hu.riskguard.datasource.internal.adapters.nav;

import org.bouncycastle.crypto.digests.SHA3Digest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Computes NAV Online Számla request signatures.
 *
 * <p>For non-manageInvoice operations (queryTaxpayer, queryInvoiceDigest, queryInvoiceData):
 * {@code requestSignature = SHA3-512(requestId + timestamp_YYYYMMDDhhmmss + signingKey).toUpperCase()}
 *
 * <p>Uses Bouncy Castle {@link SHA3Digest} directly rather than the JDK SHA3 provider
 * to ensure availability across all JDK distributions.
 */
@Component
public class SignatureService {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    /**
     * Computes the request signature for query operations.
     *
     * @param requestId  32-char UUID without hyphens, uppercase
     * @param timestamp  UTC Instant — formatted as {@code YYYYMMDDhhmmss}
     * @param signingKey the technical user's signing key (raw string)
     * @return uppercase hex-encoded SHA3-512 digest
     */
    public String computeRequestSignature(String requestId, Instant timestamp, String signingKey) {
        String timestampStr = TIMESTAMP_FMT.format(timestamp);
        String input = requestId + timestampStr + signingKey;

        SHA3Digest digest = new SHA3Digest(512);
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        digest.update(inputBytes, 0, inputBytes.length);

        byte[] output = new byte[64]; // 512 bits = 64 bytes
        digest.doFinal(output, 0);

        return bytesToHex(output).toUpperCase();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
