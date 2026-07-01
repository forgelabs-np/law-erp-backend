package com.lawfirm.erp.encryption;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DecryptionHandlerService {

    private final RSAEncryptionUtil rsaEncryptionUtil;
    private final ObjectMapper objectMapper;  // Jackson's ObjectMapper

    /**
     * Decrypt and convert to target class
     */
    public <T> T decryptRequest(String encryptedData, Class<T> targetClass) {
        try {
            String decryptedJson = decryptRequestString(encryptedData);
            T result = objectMapper.readValue(decryptedJson, targetClass);
            log.info("DECRYPT_SUCCESS - Successfully decrypted and parsed to: {}", targetClass.getSimpleName());
            return result;
        } catch (Exception e) {
            log.error("DECRYPT_FAILED - Failed to decrypt request for class: {}", targetClass.getSimpleName(), e);
            throw new RuntimeException("Failed to process encrypted request: " + e.getMessage());
        }
    }

    /**
     * Decrypt and return as JSON string
     */
    public String decryptRequestString(String encryptedData) {
        try {
            log.info("RSA_DECRYPT_START - Decrypting encrypted data");

            if (encryptedData == null || encryptedData.trim().isEmpty()) {
                throw new IllegalArgumentException("Encrypted data cannot be null or empty");
            }

            String decryptedJson = rsaEncryptionUtil.decrypt(encryptedData);
            log.info("RSA_DECRYPTED_SUCCESS - Successfully decrypted data");
            log.debug("RSA_DECRYPTED_CONTENT - Decrypted JSON: {}", decryptedJson);

            return decryptedJson;

        } catch (Exception e) {
            log.error("RSA_DECRYPT_FAILED - Failed to decrypt data", e);
            throw new RuntimeException("Failed to decrypt request: " + e.getMessage());
        }
    }

    /**
     * ENCRYPTION FOR TESTING: Convert object to JSON and encrypt
     */
    public String encryptResponse(Object responseObject) {
        try {
            log.info("RSA_ENCRYPT_START - Encrypting response of type: {}",
                    responseObject != null ? responseObject.getClass().getSimpleName() : "null");

            String jsonResponse = objectMapper.writeValueAsString(responseObject);
            String encryptedResponse = rsaEncryptionUtil.encrypt(jsonResponse);

            log.info("RSA_ENCRYPT_SUCCESS - Successfully encrypted response");
            return encryptedResponse;

        } catch (Exception e) {
            log.error("RSA_ENCRYPT_FAILED - Failed to encrypt response", e);
            throw new RuntimeException("Failed to encrypt response: " + e.getMessage());
        }
    }

    /**
     * Health check to verify encryption/decryption is working
     */
    public boolean isEncryptionWorking() {
        try {
            String testData = "{\"test\": \"rsa_health_check\", \"timestamp\": " + System.currentTimeMillis() + "}";
            String encrypted = rsaEncryptionUtil.encrypt(testData);
            String decrypted = rsaEncryptionUtil.decrypt(encrypted);
            return testData.equals(decrypted);
        } catch (Exception e) {
            log.error("RSA_ENCRYPTION_HEALTH_CHECK - FAILED: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Test RSA keys
     */
    public boolean testRSAKeys() {
        return rsaEncryptionUtil.testKeys();
    }
}