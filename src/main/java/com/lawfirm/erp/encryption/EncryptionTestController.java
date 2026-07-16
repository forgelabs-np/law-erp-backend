package com.lawfirm.erp.encryption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.enums.ApiStatus;
import com.lawfirm.erp.common.enums.Message;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.auth.request.LoginRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/encryption-test")
@RequiredArgsConstructor
public class EncryptionTestController {

    private final DecryptionHandlerService decryptionHandlerService;
    private final RSAEncryptionUtil rsaEncryptionUtil;
    private final ResponseHandler responseHandler;
    private final ObjectMapper objectMapper;

    /**
     * Step 1: Get the public key for frontend encryption
     */
    @GetMapping("/public-key")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPublicKey() {
        Map<String, String> response = new HashMap<>();
        response.put("publicKey", rsaEncryptionUtil.getPublicKeyBase64());
        response.put("message", "Use this public key to encrypt your request data");
        response.put("chunkDelimiter", "mofin");
        response.put("note", "For data > 245 bytes, split into chunks and join with 'mofin'");

        return responseHandler.ok(response, Message.SUCCESS, "Public key retrieved");
    }

    /**
     * Step 2: Encrypt any request body (for testing)
     * This simulates what the frontend would do
     */
    @PostMapping("/encrypt")
    public ResponseEntity<ApiResponse<Map<String, Object>>> encryptTestData(
            @RequestBody Map<String, Object> requestData) {
        try {
            // Check if this is a long data request (contains a special flag)
            boolean isLongData = requestData.containsKey("_generateLongData") &&
                    Boolean.TRUE.equals(requestData.get("_generateLongData"));

            Map<String, Object> dataToEncrypt;

            if (isLongData) {
                log.info("ENCRYPT_LONG_DATA - Generating and encrypting long test data");
                dataToEncrypt = generateLongTestData();
                // Remove the flag from the response
                requestData.remove("_generateLongData");
            } else {
                log.info("ENCRYPT_TEST - Received data to encrypt: {}", requestData);
                dataToEncrypt = requestData;
            }

            String encryptedData = decryptionHandlerService.encryptResponse(dataToEncrypt);

            // Count chunks
            String[] chunks = encryptedData.split("mofin");
            int chunkCount = chunks.length;
            boolean isChunked = encryptedData.contains("mofin");

            Map<String, Object> response = new HashMap<>();
            response.put("originalData", dataToEncrypt);
            response.put("encryptedData", encryptedData);
            response.put("isChunked", isChunked);
            response.put("chunkCount", chunkCount);
            response.put("totalLength", encryptedData.length());
            response.put("originalDataSize", dataToEncrypt.toString().length() + " characters");
            response.put("delimiter", "mofin");

            // Add chunk size details if chunked
            if (isChunked) {
                response.put("chunkSizes", getChunkSizes(chunks));
                response.put("firstChunkPreview", chunks.length > 0 ?
                        chunks[0].substring(0, Math.min(50, chunks[0].length())) + "..." : "");
                response.put("message", "Data encrypted with " + chunkCount + " chunks using 'mofin' delimiter");
            } else {
                response.put("message", " Data encrypted successfully (single chunk)");
            }

            log.info("ENCRYPT_TEST_SUCCESS - Data encrypted successfully, chunks: {}", chunkCount);

            return responseHandler.ok(response, Message.SUCCESS, "Data encrypted successfully");

        } catch (Exception e) {
            log.error("ENCRYPT_TEST_FAILED - Failed to encrypt data", e);
            return responseHandler.error("Encryption failed: " + e.getMessage(), ApiStatus.BAD_REQUEST, HttpStatus.CONFLICT);
        }
    }

    /**
     * Step 3: Test decryption without @Decrypt annotation
     * This shows manual decryption works
     */
    @PostMapping("/manual-decrypt")
    public ResponseEntity<ApiResponse<Map<String, Object>>> manualDecryptTest(
            @RequestBody Map<String, String> request) {
        try {
            String encryptedData = request.get("encryptedData");
            if (encryptedData == null || encryptedData.isEmpty()) {
                return responseHandler.error("encryptedData field is required", ApiStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST);
            }

            log.info("MANUAL_DECRYPT - Decrypting data manually");

            boolean isChunked = encryptedData.contains("mofin");
            int chunkCount = isChunked ? encryptedData.split("mofin").length : 1;

            log.info("MANUAL_DECRYPT - Data is chunked: {}, chunks: {}", isChunked, chunkCount);

            String decryptedJson = decryptionHandlerService.decryptRequestString(encryptedData);
            Map<String, Object> decryptedMap = objectMapper.readValue(decryptedJson, Map.class);

            Map<String, Object> response = new HashMap<>();
            response.put("decryptedData", decryptedMap);
            response.put("decryptedJson", decryptedJson);
            response.put("isChunked", isChunked);
            response.put("chunkCount", chunkCount);
            response.put("decryptedDataSize", decryptedJson.length() + " characters");

            if (isChunked) {
                response.put("message", "Successfully decrypted " + chunkCount + " chunks of data!");
                response.put("note", "The 'mofin' delimiter was used to split and reassemble the data");
            } else {
                response.put("message", "Successfully decrypted single chunk of data");
            }

            log.info("MANUAL_DECRYPT_SUCCESS - Data decrypted successfully, chunks: {}", chunkCount);

            return responseHandler.ok(response, Message.SUCCESS, "Data decrypted successfully");

        } catch (Exception e) {
            log.error("MANUAL_DECRYPT_FAILED", e);
            return responseHandler.error("Manual decryption failed: " + e.getMessage(), ApiStatus.BAD_REQUEST, HttpStatus.CONFLICT);
        }
    }
    /**
     * Step 4: AUTO DECRYPTION with @Decrypt annotation
     * This is what your actual endpoints will use
     */
    @PostMapping("/auto-decrypt")
    @Decrypt
    public ResponseEntity<ApiResponse<Map<String, Object>>> autoDecryptTest(
            @RequestBody ApiRequest<LoginRequest> request) {
        try {
            log.info("AUTO_DECRYPT_TEST - Request automatically decrypted!");
            log.info("Decrypted Data: {}", request.getData());

            Map<String, Object> response = new HashMap<>();
            response.put("message", " Auto-decryption worked successfully!");
            response.put("decryptedData", request.getData());
            response.put("username", request.getData().getUsername());
            response.put("note", "This endpoint uses @Decrypt annotation - no manual decryption needed");

            return responseHandler.ok(response, Message.SUCCESS, "Auto-decryption successful");

        } catch (Exception e) {
            log.error("AUTO_DECRYPT_TEST_FAILED", e);
            return responseHandler.error("Auto-decryption test failed: " + e.getMessage(), ApiStatus.BAD_REQUEST, HttpStatus.CONFLICT);
        }
    }

    /**
     * Step 5: Demo full flow - Shows encrypted request being auto-decrypted
     * This simulates your actual login endpoint
     */
    @PostMapping("/demo-login")
    @Decrypt
    public ResponseEntity<ApiResponse<Map<String, Object>>> demoLogin(
            @RequestBody ApiRequest<LoginRequest> request) {
        try {
            log.info("DEMO_LOGIN - Login request received!");
            log.info("Username: {}", request.getData().getUsername());
            log.info("Password: {}", request.getData().getPassword());

            // Simulate authentication
            boolean authenticated = "admin".equals(request.getData().getUsername())
                    && "password123".equals(request.getData().getPassword());

            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", authenticated);
            response.put("username", request.getData().getUsername());
            response.put("message", authenticated ? "✅ Login successful!" : "❌ Invalid credentials");
            response.put("note", "This request was automatically decrypted using @Decrypt");

            if (authenticated) {
                response.put("token", "demo-jwt-token-" + System.currentTimeMillis());
                response.put("role", "ADMIN");
            }

            return responseHandler.ok(response,
                    authenticated ? Message.SUCCESS : Message.INTERNAL_SERVER_ERROR,
                    authenticated ? "Login successful" : "Login failed");

        } catch (Exception e) {
            log.error("DEMO_LOGIN_FAILED", e);
            return responseHandler.error("Demo login failed: " + e.getMessage(), ApiStatus.BAD_REQUEST, HttpStatus.CONFLICT);
        }
    }

    /**
     * Health check for encryption
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> encryptionHealth() {
        try {
            boolean isWorking = decryptionHandlerService.isEncryptionWorking();
            boolean keysLoaded = rsaEncryptionUtil.testKeys();

            Map<String, Object> health = new HashMap<>();
            health.put("encryptionWorking", isWorking);
            health.put("keysLoaded", keysLoaded);
            health.put("status", isWorking && keysLoaded ? "HEALTHY" : "UNHEALTHY");
            health.put("publicKeyAvailable", rsaEncryptionUtil.getPublicKeyBase64() != null);

            return responseHandler.ok(health, Message.SUCCESS, "Encryption health check");

        } catch (Exception e) {
            return responseHandler.error("Health check failed: " + e.getMessage(), ApiStatus.BAD_REQUEST, HttpStatus.CONFLICT);
        }
    }
    private Map<String, Object> generateLongTestData() {
        Map<String, Object> largeData = new HashMap<>();
        largeData.put("username", "admin");
        largeData.put("password", "password123");
        largeData.put("email", "admin@company.com");
        largeData.put("phone", "+977-9841234567");
        largeData.put("address", "Kathmandu, Nepal");
        largeData.put("department", "IT Department");
        largeData.put("role", "SUPER_ADMIN");
        largeData.put("permissions", new String[]{
                "READ", "WRITE", "DELETE", "UPDATE", "MANAGE_USERS",
                "MANAGE_ROLES", "VIEW_REPORTS", "EXPORT_DATA"
        });

        // Add a large nested object
        Map<String, Object> profile = new HashMap<>();
        profile.put("firstName", "John");
        profile.put("lastName", "Doe");
        profile.put("age", 35);
        profile.put("bio", "Experienced software developer with over 10 years of experience");
        profile.put("skills", new String[]{
                "Java", "Spring Boot", "React", "Angular", "Docker",
                "Kubernetes", "AWS", "MySQL", "MongoDB", "Redis",
                "Kafka", "RabbitMQ", "Microservices", "REST APIs"
        });
        largeData.put("profile", profile);

        // Add a long string to exceed 245 bytes
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            longText.append("This is a very long text to ensure we exceed the RSA encryption chunk size limit. ");
        }
        largeData.put("longDescription", longText.toString());

        largeData.put("_isLongData", true);
        largeData.put("_note", "This data exceeds 245 bytes and will be chunked with 'mofin' delimiter");

        log.info("Generated long test data with size: {} characters", largeData.toString().length());
        return largeData;
    }

    /**
     * Get sizes of each chunk
     */
    private Map<String, Integer> getChunkSizes(String[] chunks) {
        Map<String, Integer> sizes = new HashMap<>();
        for (int i = 0; i < chunks.length; i++) {
            sizes.put("chunk_" + (i + 1), chunks[i].length());
        }
        return sizes;
    }
}