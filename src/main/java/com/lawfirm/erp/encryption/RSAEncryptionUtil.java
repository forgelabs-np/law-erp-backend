package com.lawfirm.erp.encryption;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
public class RSAEncryptionUtil {

    // Load keys from application.properties instead of hardcoding
    @Value("${encryption.rsa.public-key:}")
    private String rsaPublicKey;

    @Value("${encryption.rsa.private-key:}")
    private String rsaPrivateKey;

    private static final String RSA_ALGORITHM = "RSA/ECB/PKCS1Padding";
    private static final String CHUNK_DELIMITER = "mofin";
    private static final int MAX_RSA_CHUNK_SIZE = 245; // bytes for 2048-bit RSA

    private PrivateKey privateKey;
    private PublicKey publicKey;

    // SECURITY FIX: Removed hardcoded fallback keys. RSA keys MUST be configured
    // via application properties (encryption.rsa.public-key and encryption.rsa.private-key).
    // If keys are not configured, initialization will fail at startup rather than
    // silently using insecure defaults.

    public RSAEncryptionUtil() {
        try {
            if (rsaPublicKey == null || rsaPublicKey.isEmpty()) {
                throw new IllegalArgumentException(
                        "RSA public key not configured. Set 'encryption.rsa.public-key' in application properties."
                );
            }
            if (rsaPrivateKey == null || rsaPrivateKey.isEmpty()) {
                throw new IllegalArgumentException(
                        "RSA private key not configured. Set 'encryption.rsa.private-key' in application properties."
                );
            }

            String publicKeyToUse = rsaPublicKey;
            String privateKeyToUse = rsaPrivateKey;

            String cleanedPublicKey = cleanBase64Key(publicKeyToUse);
            String cleanedPrivateKey = cleanBase64Key(privateKeyToUse);

            // Load public key
            byte[] publicKeyBytes = Base64.getDecoder().decode(cleanedPublicKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            this.publicKey = keyFactory.generatePublic(publicKeySpec);

            // Load private key
            byte[] privateKeyBytes = Base64.getDecoder().decode(cleanedPrivateKey);
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            this.privateKey = keyFactory.generatePrivate(privateKeySpec);

            log.info("RSA_KEYS_LOADED - RSA keys loaded successfully");

        } catch (Exception e) {
            log.error("RSA_KEYS_LOAD_FAILED - Failed to load RSA keys", e);
            throw new RuntimeException("Failed to load RSA keys: " + e.getMessage(), e);
        }
    }

    private String cleanBase64Key(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        String cleaned = key.replaceAll("[^A-Za-z0-9+/=]", "");
        int padding = cleaned.length() % 4;
        if (padding > 0) {
            cleaned += "=".repeat(4 - padding);
        }
        return cleaned;
    }

    public String decrypt(String encryptedData) {
        try {
            log.debug("RSA_DECRYPT_START - Starting RSA decryption");

            if (encryptedData.contains(CHUNK_DELIMITER)) {
                log.info("CHUNKED_DATA_DETECTED - Decrypting chunked data");
                return decryptChunkedData(encryptedData);
            } else {
                return decryptChunk(encryptedData);
            }

        } catch (Exception e) {
            log.error("RSA_DECRYPT_FAILED - RSA decryption failed", e);
            throw new RuntimeException("RSA decryption failed: " + e.getMessage(), e);
        }
    }

    private String decryptChunk(String encryptedChunk) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedChunk);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("CHUNK_DECRYPT_FAILED - Failed to decrypt chunk", e);
            throw new RuntimeException("Chunk decryption failed: " + e.getMessage(), e);
        }
    }

    private String decryptChunkedData(String encryptedData) {
        try {
            String[] chunks = encryptedData.split(CHUNK_DELIMITER);
            log.info("DECRYPTING_CHUNKS - Processing {} chunks", chunks.length);

            StringBuilder decryptedResult = new StringBuilder();
            for (int i = 0; i < chunks.length; i++) {
                log.debug("Decrypting chunk {}/{}", i + 1, chunks.length);
                String decryptedChunk = decryptChunk(chunks[i]);
                decryptedResult.append(decryptedChunk);
            }

            log.info("CHUNKED_DECRYPTION_SUCCESS - Successfully decrypted {} chunks", chunks.length);
            return decryptedResult.toString();

        } catch (Exception e) {
            log.error("CHUNKED_DECRYPT_FAILED - Failed to decrypt chunked data", e);
            throw new RuntimeException("Chunked decryption failed: " + e.getMessage(), e);
        }
    }

    public String getChunkDelimiter() {
        return CHUNK_DELIMITER;
    }

    // ENCRYPTION METHODS (FOR TESTING)
    public String encrypt(String plainText) {
        try {
            log.debug("RSA_ENCRYPT_START - Starting RSA encryption");
            byte[] plainTextBytes = plainText.getBytes(StandardCharsets.UTF_8);

            if (plainTextBytes.length <= MAX_RSA_CHUNK_SIZE) {
                return encryptChunk(plainText);
            }

            List<String> encryptedChunks = new ArrayList<>();
            String plainTextStr = plainText;
            int textLength = plainTextStr.length();
            int chunkSize = calculateChunkSizeForString(plainTextStr);

            int index = 0;
            while (index < textLength) {
                String chunk = plainTextStr.substring(index, Math.min(index + chunkSize, textLength));
                String encryptedChunk = encryptChunk(chunk);
                encryptedChunks.add(encryptedChunk);
                index += chunkSize;
            }

            return String.join(CHUNK_DELIMITER, encryptedChunks);

        } catch (Exception e) {
            log.error("RSA_ENCRYPT_FAILED - RSA encryption failed", e);
            throw new RuntimeException("RSA encryption failed: " + e.getMessage(), e);
        }
    }

    private String encryptChunk(String chunk) {
        try {
            byte[] chunkBytes = chunk.getBytes(StandardCharsets.UTF_8);
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedBytes = cipher.doFinal(chunkBytes);
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            log.error("CHUNK_ENCRYPT_FAILED - Failed to encrypt chunk", e);
            throw new RuntimeException("Chunk encryption failed: " + e.getMessage(), e);
        }
    }

    private int calculateChunkSizeForString(String text) {
        int conservativeChunkSize = MAX_RSA_CHUNK_SIZE / 4;
        return Math.max(1, conservativeChunkSize);
    }

    public boolean testKeys() {
        try {
            String testData = "Hello, RSA Encryption Test!";
            String encrypted = encrypt(testData);
            String decrypted = decrypt(encrypted);
            boolean success = testData.equals(decrypted);
            log.info("RSA_KEY_TEST_RESULT - Test: {}", success ? "PASSED" : "FAILED");
            return success;
        } catch (Exception e) {
            log.error("RSA_KEY_TEST_FAILED - RSA Key test failed: {}", e.getMessage());
            return false;
        }
    }

    public String getPublicKeyBase64() {
        if (rsaPublicKey == null || rsaPublicKey.isEmpty()) {
            throw new IllegalStateException("RSA public key not configured");
        }
        return rsaPublicKey;
    }
}