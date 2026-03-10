package hu.riskguard.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for generating SHA-256 hashes for the audit trail.
 * Used by the screening module to create legal-proof hashes in {@code search_audit_log}.
 */
public final class HashUtil {

    private HashUtil() {
        // Static utility — no instantiation
    }

    /**
     * Concatenates all input parts and returns a hex-encoded SHA-256 hash.
     *
     * @param parts one or more strings to hash
     * @return lowercase hex-encoded SHA-256 digest (64 characters)
     * @throws IllegalStateException if SHA-256 algorithm is not available (should never happen on standard JVMs)
     */
    public static String sha256(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                if (part != null) {
                    digest.update(part.getBytes(StandardCharsets.UTF_8));
                }
            }
            byte[] hash = digest.digest();
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
