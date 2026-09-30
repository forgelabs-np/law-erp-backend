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

    // For testing - fallback keys if not in properties
    private static final String FALLBACK_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlA31xNwr4uAW+qT7+3XNd7lLS0xn1W5tgNyJVpR86dWOhPotnQhnasQOode80+AFgPz1bAjTAWSZfxLScnq65lH1ZdQJFydFQawKSMcVmelrXmq51lE//n7yUTXkG8DUD1rf6QY2vrI44gY+sjXT844qeHU+L89Xjk7BOK2S5v8WdYugvqD3krToPEgZfMTbSP2Fxztc1biXFvyEGxPuEGlniW3U7JXYmI27nBRYf8X1XIE1Xfg6Xo7JHeK2Ey+f0lu9Zxin6KUcFz2K/E2KZ6mBvebeXkvQ6+jZojdclkieN9aJQVn7A6W95uksAG+BAUy98Ff/8K8m3beghJnHDQIDAQAB";
    private static final String FALLBACK_PRIVATE_KEY = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCUDfXE3Cvi4Bb6pPv7dc13uUtLTGfVbm2A3IlWlHzp1Y6E+i2dCGdqxA6h17zT4AWA/PVsCNMBZJl/EtJyerrmUfVl1AkXJ0VBrApIxxWZ6WtearnWUT/+fvJRNeQbwNQPWt/pBja+sjjiBj6yNdPzjip4dT4vz1eOTsE4rZLm/xZ1i6C+oPeStOg8SBl8xNtI/YXHO1zVuJcW/IQbE+4QaWeJbdTsldiYjbucFFh/xfVcgTVd+Dpejskd4rYTL5/SW71nGKfopRwXPYr8TYpnqYG95t5eS9Dr6NmiN1yWSJ431olBWfsDpb3m6SwAb4EBTL3wV//wrybdt6CEmccNAgMBAAECggEAC4UwkAJfydYNA7DNyKnIdJ3u7WuDEtj2XVYLu7hvJdTPs6ox3Wu3fFfIGbDLSSM/2mMUh7UCEjQtO3WP+YdyPpS3BxbiJDKSkYMdA7+1/xVqHN0qG7KTy64+FbKfTseI2K8GCEBLBzif83pv63cDyIpR5LCex6KXGgQnxcy/xwiI9Vh49V+t0v4t0Rs/wonLwOXD/LVapmwzsp2Ai2SJwgxJy/Qo49qxq/acidkg5YqlOSluL3SK1YWDE6bDMKQoIyA5Vvt04id7NixDn1EuclzD8fATZ44Op1tISZHoKZ7ediw96BMYFaabpByOdVXra4jsuxmO78YMUiqDPAVU4QKBgQDMuisiO8aqfJrUVyZ8+m9+ws34hO+FZDgjySB6HQda4jNjhhyKCeGtgAkrFnlXwQgYkjgHKnIRnzOuvzuIp+tOO/Luyxk5QeGqi358GeWlkgQCsY6juCvgkPNDLX2d1Ff6gdgUUi12jnnB61cLGtERal/SKQcUcFGTNEnFl12bcQKBgQC5IkreDSZLpaBZj0jGXMFUQaKktEKv3v9gqeSdidRkdtMNXQD6kDG4QMo35ODrrV634IRmdQzPD2i6Rt2nSWn7Bt3aOHWctIzoKcFT1NYY0MQtwznQsXJvCrdbOefEje4gFPBfFLlmBgrP2FHeIpGsCKnjq31/IDs8DOK6ZQ+/XQKBgEnelre4d9uGMFuTwphvyJEleypD1ST9X2BSLvzAwqmhWsd7WYrZO+vdefFpH4lxZhlvkPXM8/G1zvEroTCS3k2RRfuxnr1RLzrZMF9Y/Mq8H+RU6tHaH0LdKlk/7cZoGwKRnUTfzfWsPPSilPq1x2AQUNjE4wAV8uk5gbDhB+6RAoGBAJA8+3+NVyzQ2eFtFRIW6jku+fzAxMQpRWaWdxuWavfq6/wZXc3Z0iLvt51coTB9XrJ8Jit9PoGES9/1nnPbasq9StPd8SQqNy4aehlKVZP38yCEXeMOnU2OV9SnhL9KpSAxsCUkDF5Ejt/odcBPxpb2GQbccWY+QmhC0dLPMjmxAoGAdBHG+aWdXC5E/QUAhoTkqhHbtcgC97rtMkxWzkZTQjR17IcoIF6XvNVc6/sBEWoUDgLbuRusBv02iXdeOV3XhfBoQgMxy2+QGV9gy5HeSX/ZV/S90uTuQIQNXfmkB6A/N65dFF3lvxcGFYhaW9itolUed5tcp+iJ+2d3ylGuIio=";

    public RSAEncryptionUtil() {
        try {
            String publicKeyToUse = (rsaPublicKey == null || rsaPublicKey.isEmpty())
                    ? FALLBACK_PUBLIC_KEY : rsaPublicKey;
            String privateKeyToUse = (rsaPrivateKey == null || rsaPrivateKey.isEmpty())
                    ? FALLBACK_PRIVATE_KEY : rsaPrivateKey;

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
        return rsaPublicKey != null && !rsaPublicKey.isEmpty() ? rsaPublicKey : FALLBACK_PUBLIC_KEY;
    }
}