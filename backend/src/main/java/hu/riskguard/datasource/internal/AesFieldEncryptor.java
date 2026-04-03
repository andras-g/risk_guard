package hu.riskguard.datasource.internal;

import hu.riskguard.core.config.RiskGuardProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-CBC field-level encryptor for sensitive credentials stored in the database.
 *
 * <p>Key derivation: {@code PBKDF2WithHmacSHA256(JWT_SECRET, "nav-cred-salt", 65536, 256)}.
 * This avoids using the JWT secret directly as an AES key (which may be shorter than 32 bytes).
 *
 * <p>Encrypted values are stored as Base64-encoded strings in the format:
 * {@code base64(iv) + ":" + base64(ciphertext)}.
 */
@Component
public class AesFieldEncryptor {

    private static final Logger log = LoggerFactory.getLogger(AesFieldEncryptor.class);
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final byte[] SALT = "nav-cred-salt".getBytes();
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final int IV_LENGTH = 16;

    private final RiskGuardProperties properties;
    private SecretKey secretKey;

    public AesFieldEncryptor(RiskGuardProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() throws Exception {
        String jwtSecret = properties.getSecurity().getJwtSecret();
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(jwtSecret.toCharArray(), SALT, ITERATIONS, KEY_LENGTH);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.debug("AesFieldEncryptor key derived via PBKDF2WithHmacSHA256");
    }

    /**
     * Encrypts {@code plaintext} and returns a Base64-encoded string: {@code base64(iv):base64(ciphertext)}.
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a Base64-encoded {@code iv:ciphertext} string and returns the original plaintext.
     */
    public String decrypt(String encryptedValue) {
        try {
            String[] parts = encryptedValue.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid encrypted value format — expected iv:ciphertext");
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
