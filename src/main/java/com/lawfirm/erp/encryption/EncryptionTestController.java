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
            log.info("ENCRYPT_TEST - Received data to encrypt: {}", requestData);

            String encryptedData = decryptionHandlerService.encryptResponse(requestData);

            Map<String, Object> response = new HashMap<>();
            response.put("originalData", requestData);
            response.put("encryptedData", encryptedData);
            response.put("isChunked", encryptedData.contains("mofin"));
            response.put("length", encryptedData.length());

            log.info("ENCRYPT_TEST_SUCCESS - Data encrypted successfully");

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

            String decryptedJson = decryptionHandlerService.decryptRequestString(encryptedData);
            Map<String, Object> decryptedMap = objectMapper.readValue(decryptedJson, Map.class);

            Map<String, Object> response = new HashMap<>();
            response.put("decryptedData", decryptedMap);
            response.put("decryptedJson", decryptedJson);
            response.put("isChunked", encryptedData.contains("mofin"));

            log.info("MANUAL_DECRYPT_SUCCESS - Data decrypted successfully");

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
}