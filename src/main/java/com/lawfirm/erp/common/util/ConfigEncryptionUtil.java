package com.lawfirm.erp.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256 GCM encrypt/decrypt for sensitive config values (SMTP passwords).
 *
 * Key is read from application.properties: config.encryption.key
 * Must be a 32-byte Base64-encoded string (44 chars Base64).
 *
 * GCM mode provides authenticated encryption — tampered ciphertext is detected.
 * Each encryption generates a random 12-byte IV, prepended to the ciphertext.
 */
@Slf4j
@Component
public class ConfigEncryptionUtil {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;     // 96-bit IV recommended for GCM
    private static final int GCM_TAG_LENGTH = 128;    // 128-bit authentication tag

    private final SecretKey secretKey;

    public ConfigEncryptionUtil(@Value("${config.encryption.key}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException(
                    "Config encryption key not set. Add 'config.encryption.key' to application.yml " +
                    "(32-byte value, Base64-encoded)."
            );
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "Config encryption key must be 32 bytes (256 bits). Got " + keyBytes.length + " bytes."
            );
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("ConfigEncryptionUtil initialized with 256-bit AES key");
    }

    /**
     * Encrypt a plaintext string.
     * Returns Base64( IV (12 bytes) + ciphertext ).
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext: IV (12) + ciphertext (variable)
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            log.error("AES encryption failed", e);
            throw new RuntimeException("Failed to encrypt config value", e);
        }
    }

    /**
     * Decrypt a string previously encrypted by {@link #encrypt(String)}.
     * Expects Base64( IV (12 bytes) + ciphertext ).
     */
    public String decrypt(String encryptedData) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedData);

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES decryption failed — data may be corrupted or key has changed", e);
            throw new RuntimeException("Failed to decrypt config value", e);
        }
    }
}
