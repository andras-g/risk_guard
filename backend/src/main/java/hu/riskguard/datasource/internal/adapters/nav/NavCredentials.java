package hu.riskguard.datasource.internal.adapters.nav;

/**
 * Decrypted NAV Online Számla technical user credentials for a single tenant.
 *
 * <p>{@code login} and {@code signingKey} and {@code exchangeKey} are stored encrypted
 * at rest via {@link hu.riskguard.datasource.internal.AesFieldEncryptor}.
 * {@code passwordHash} is the SHA-512 uppercase hex digest of the raw password
 * (never stored plaintext).
 *
 * @param login        NAV technical user login name
 * @param passwordHash SHA-512(rawPassword).toUpperCase() — sent as-is to NAV API
 * @param signingKey   signing key for request signature computation (SHA3-512)
 * @param exchangeKey  AES-128 key for tokenExchange response decryption
 * @param taxNumber    technical user's own 8-digit Hungarian tax number (required by NAV UserHeaderType)
 */
public record NavCredentials(
        String login,
        String passwordHash,
        String signingKey,
        String exchangeKey,
        String taxNumber
) {}
