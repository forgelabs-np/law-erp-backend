package com.lawfirm.erp.encryption;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class DecryptionRequestBodyAdvice implements RequestBodyAdvice {

    private final DecryptionHandlerService decryptionHandlerService;
    private final HttpServletRequest httpServletRequest;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        boolean isSupported = methodParameter.hasMethodAnnotation(Decrypt.class) ||
                methodParameter.getContainingClass().isAnnotationPresent(Decrypt.class);

        log.debug("RSA_Decryption support check for {}.{}: {}",
                methodParameter.getContainingClass().getSimpleName(),
                methodParameter.getMethod().getName(),
                isSupported);

        return isSupported;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage,
                                           MethodParameter parameter,
                                           Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        try {
            log.info("RSA_AUTO_DECRYPT_START - Automatic RSA decryption triggered for: {}", targetType.getTypeName());

            // Read the request body
            InputStream originalBody = inputMessage.getBody();
            String requestBody = new String(originalBody.readAllBytes(), StandardCharsets.UTF_8).trim();

            log.debug("RSA_REQUEST_BODY_RECEIVED - Request body length: {} characters", requestBody.length());
            log.debug("RSA_REQUEST_BODY_PREVIEW - First 100 chars: {}",
                    requestBody.length() > 100 ? requestBody.substring(0, 100) + "..." : requestBody);

            // Check if decryption is enabled
            Decrypt decryptAnnotation = parameter.getMethodAnnotation(Decrypt.class);
            boolean shouldDecrypt = decryptAnnotation == null || decryptAnnotation.enabled();

            if (!shouldDecrypt) {
                log.info("RSA_DECRYPT_SKIPPED - Decryption disabled for this method");
                return new DecryptedHttpInputMessage(requestBody, inputMessage.getHeaders());
            }

            // Check enc header - encryption is MANDATORY for @Decrypt
            String encHeader = httpServletRequest.getHeader("enc");
            boolean isEncrypted = encHeader != null && Boolean.parseBoolean(encHeader);

            if (!isEncrypted) {
                log.error("RSA_SECURITY_VIOLATION - @Decrypt method received plain text request");
                throw new SecurityException("Invalid request format - 'enc' header must be true");
            }

            log.info("RSA_DECRYPTING_REQUEST - Starting RSA decryption process");

            // Clean the request body
            String cleanedRequestBody = cleanRequestBody(requestBody);
            log.debug("RSA_CLEANED_BODY - Length after cleaning: {} characters", cleanedRequestBody.length());

            // Validate encrypted data format
            validateRSAEncryptedDataFormat(cleanedRequestBody);

            // Decrypt the entire request body
            String decryptedJson = decryptionHandlerService.decryptRequestString(cleanedRequestBody);

            // Handle ApiRequest wrapper if needed
            String finalJson = wrapInApiRequestIfNeeded(decryptedJson, parameter);

            log.info("RSA_AUTO_DECRYPT_SUCCESS - Successfully decrypted request body");

            return new DecryptedHttpInputMessage(finalJson, inputMessage.getHeaders());

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("RSA_AUTO_DECRYPT_FAILED - Automatic RSA decryption failed", e);
            throw new SecurityException("Request processing failed: " + e.getMessage());
        }
    }

    /**
     * Wraps decrypted data in ApiRequest if needed
     */
    private String wrapInApiRequestIfNeeded(String decryptedJson, MethodParameter parameter) {
        try {
            Type targetType = parameter.getGenericParameterType();
            String typeName = targetType.getTypeName();

            if (typeName.contains("ApiRequest")) {
                if (!decryptedJson.trim().startsWith("{") || !decryptedJson.contains("\"data\"")) {
                    String wrapped = "{\"data\": " + decryptedJson + "}";
                    log.debug("RSA_WRAPPED_IN_API_REQUEST - Wrapped decrypted data in ApiRequest");
                    return wrapped;
                }
            }
            return decryptedJson;

        } catch (Exception e) {
            log.warn("RSA_WRAP_FAILED - Could not wrap in ApiRequest: {}", e.getMessage());
            return decryptedJson;
        }
    }

    private String cleanRequestBody(String requestBody) {
        if (requestBody == null || requestBody.trim().isEmpty()) {
            return requestBody;
        }

        String cleaned = requestBody.trim();

        // Remove surrounding double quotes if present
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
            log.debug("RSA_QUOTES_REMOVED - Removed surrounding quotes");
        }

        // Remove surrounding single quotes if present
        if (cleaned.startsWith("'") && cleaned.endsWith("'")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
            log.debug("RSA_SINGLE_QUOTES_REMOVED - Removed surrounding single quotes");
        }

        return cleaned;
    }

    private void validateRSAEncryptedDataFormat(String requestBody) {
        if (requestBody == null || requestBody.trim().isEmpty()) {
            log.warn("RSA_SECURITY_WARNING - Empty request body");
            throw new SecurityException("Invalid request format");
        }

        String[] parts = requestBody.split("mofin");
        log.debug("RSA_VALIDATION - Validating {} chunk(s)", parts.length);

        for (int i = 0; i < parts.length; i++) {
            String chunk = parts[i].trim();
            try {
                Base64.getDecoder().decode(chunk);
                log.debug("RSA_CHUNK_VALIDATED - Chunk {}/{} is valid Base64", i + 1, parts.length);
            } catch (IllegalArgumentException e) {
                log.warn("RSA_SECURITY_WARNING - Invalid Base64 in chunk {}/{}", i + 1, parts.length);
                throw new SecurityException("Invalid request format");
            }
        }
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        log.debug("RSA_DTO_READY - Request body converted to: {}",
                body != null ? body.getClass().getSimpleName() : "null");
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                  Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    private static class DecryptedHttpInputMessage implements HttpInputMessage {
        private final String decryptedJson;
        private final HttpHeaders headers;

        public DecryptedHttpInputMessage(String decryptedJson, HttpHeaders headers) {
            this.decryptedJson = decryptedJson;
            this.headers = headers;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(decryptedJson.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}