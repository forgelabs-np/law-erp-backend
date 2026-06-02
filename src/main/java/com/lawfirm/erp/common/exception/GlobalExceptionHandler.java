package com.lawfirm.erp.common.exception;

import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.enums.ApiStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final ResponseHandler responseHandler;

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return responseHandler.error("Invalid username or password", ApiStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(UnauthorizedException ex) {
        log.warn("Unauthorized: {}", ex.getMessage());
        return responseHandler.error(ex.getMessage(), ApiStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbiddenException(ForbiddenException ex) {
        log.warn("Forbidden: {}", ex.getMessage());
        return responseHandler.error(ex.getMessage(), ApiStatus.FORBIDDEN, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(ModuleNotEnabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleModuleNotEnabledException(ModuleNotEnabledException ex) {
        log.warn("Module not enabled: {}", ex.getMessage());
        return responseHandler.error(ex.getMessage(), ApiStatus.FORBIDDEN, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantNotFoundException(TenantNotFoundException ex) {
        log.warn("Tenant not found: {}", ex.getMessage());
        return responseHandler.error(ex.getMessage(), ApiStatus.NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        log.error("Error: {}", ex.getMessage());
        return responseHandler.error(ex.getMessage(), ApiStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);
        return responseHandler.error("An unexpected error occurred", ApiStatus.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}