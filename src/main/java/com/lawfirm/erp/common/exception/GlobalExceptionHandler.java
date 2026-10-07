package com.lawfirm.erp.common.exception;

import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.enums.ApiStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final ResponseHandler responseHandler;

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        // Surface the specific, already-sanitised reason (firm suspended, account locked, portal
        // disabled…). True credential failures are thrown as "Invalid username or password", so
        // anti-enumeration is preserved where it matters.
        String message = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage() : "Invalid username or password";
        return responseHandler.error(message, ApiStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        log.warn("Unauthorized: {}", ex.getMessage());
        return responseHandler.error(ex.getMessage(), ApiStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        log.warn("Forbidden: {}", ex.getMessage());
        return responseHandler.error(ex.getMessage(), ApiStatus.FORBIDDEN, HttpStatus.FORBIDDEN);
    }

    // Spring Security @PreAuthorize denials
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return responseHandler.error("You do not have permission to perform this action",
                ApiStatus.FORBIDDEN, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("Validation failed: {}", message);
        return responseHandler.error(message, ApiStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Not found: {}", ex.getMessage());
        return responseHandler.error(ex.getMessage(), ApiStatus.NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantNotFound(TenantNotFoundException ex) {
        log.warn("Tenant not found: {}", ex.getMessage());
        return responseHandler.error(ex.getMessage(), ApiStatus.NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ModuleNotEnabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleModuleNotEnabled(ModuleNotEnabledException ex) {
        log.warn("Module not enabled: {}", ex.getMessage());
        return responseHandler.error(ex.getMessage(), ApiStatus.FORBIDDEN, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return responseHandler.error(ex.getMessage(), ApiStatus.BAD_REQUEST, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(BusinessRuleException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return responseHandler.error(ex.getMessage(), ApiStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(StorageOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorageOperation(StorageOperationException ex) {
        log.error("Object storage failure: {}", ex.getMessage(), ex);
        return responseHandler.error(
                "Document storage is temporarily unavailable. Please try again.",
                ApiStatus.INTERNAL_ERROR, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: {}", ex.getMessage());

        // The top-level Hibernate message does not carry the offending columns; the driver's
        // most-specific cause does ("Key (username, firm_id)=(…)"). Match against that.
        Throwable root = ex.getMostSpecificCause();
        String msg = root != null ? root.getMessage() : ex.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("username")) return responseHandler.error(
                    "Username already exists", ApiStatus.BAD_REQUEST, HttpStatus.CONFLICT);
            if (lower.contains("email")) return responseHandler.error(
                    "Email already exists", ApiStatus.BAD_REQUEST, HttpStatus.CONFLICT);
            if (lower.contains("mobile")) return responseHandler.error(
                    "Mobile number already exists", ApiStatus.BAD_REQUEST, HttpStatus.CONFLICT);
            if (lower.contains("law_firm_code") || lower.contains("lawfirmcode")) return responseHandler.error(
                    "Firm code already exists", ApiStatus.BAD_REQUEST, HttpStatus.CONFLICT);
        }

        return responseHandler.error("A record with this data already exists",
                ApiStatus.BAD_REQUEST, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        String message = "Required parameter '" + ex.getParameterName() + "' is missing";
        log.warn("Missing parameter: {}", message);
        return responseHandler.error(message, ApiStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'";
        log.warn("Type mismatch: {}", message);
        return responseHandler.error(message, ApiStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMessage());
        return responseHandler.error("HTTP method not supported for this endpoint",
                ApiStatus.BAD_REQUEST, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("Endpoint not found: {}", ex.getMessage());
        return responseHandler.error("Endpoint not found", ApiStatus.NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        log.error("Runtime error: {}", ex.getMessage());
        return responseHandler.error(ex.getMessage(), ApiStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected error: ", ex);
        return responseHandler.error("An unexpected error occurred",
                ApiStatus.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}