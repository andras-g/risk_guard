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
     * Null byte used as a part separator to prevent part-boundary collision attacks.
     *
     * <p>Without a separator, {@code sha256("ab", "cd")} equals {@code sha256("abcd")},
     * meaning an attacker could craft two different input sets producing the same hash.
     * In a legal proof audit trail this is unacceptable.
     *
     * <p><strong>Important:</strong> Existing hashes stored in {@code search_audit_log} before
     * this fix (Stories 2.1–2.5) were computed without the separator. Those rows remain valid
     * historical records but cannot be recomputed with this method. A {@code hash_version}
     * column will be added in Story 6.4 (Admin Audit Viewer) to distinguish them.
     */
    private static final byte SEPARATOR = 0x00;

    /**
     * Hashes each part individually (separated by a null byte) and returns a hex-encoded SHA-256 digest.
     *
     * <p>Each part is fed to the digest followed by a {@code 0x00} separator byte, preventing
     * part-boundary collisions: {@code sha256("ab","cd")} != {@code sha256("abcd")}.
     *
     * @param parts one or more strings to hash
     * @return lowercase hex-encoded SHA-256 digest (64 characters)
     * @throws IllegalArgumentException if any part is null (null inputs cause ambiguous hashes)
     * @throws IllegalStateException    if SHA-256 algorithm is not available (never on standard JVMs)
     */
    public static String sha256(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                if (part == null) {
                    throw new IllegalArgumentException(
                            "Null part in SHA-256 hash input — null values cause hash collisions " +
                            "in the legal audit trail (sha256(\"a\", null, \"b\") == sha256(\"a\", \"b\"))");
                }
                digest.update(part.getBytes(StandardCharsets.UTF_8));
                digest.update(SEPARATOR);
            }
            byte[] hash = digest.digest();
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
