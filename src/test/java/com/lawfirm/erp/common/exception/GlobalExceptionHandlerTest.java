package com.lawfirm.erp.common.exception;

import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.enums.ApiStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Regression tests: wrong HTTP method on an existing endpoint must return 405
 * (was 500), and an unknown path must return 404 (was 500).
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock private ResponseHandler responseHandler;

    private GlobalExceptionHandler handler() {
        when(responseHandler.error(anyString(), any(ApiStatus.class), any(HttpStatus.class)))
                .thenAnswer(inv -> ResponseEntity.status(inv.getArgument(2)).body(null));
        return new GlobalExceptionHandler(responseHandler);
    }

    @Test
    @DisplayName("Wrong HTTP method → 405 Method Not Allowed (was 500)")
    void wrongMethod_returns405() {
        ResponseEntity<ApiResponse<Void>> response =
                handler().handleMethodNotSupported(new HttpRequestMethodNotSupportedException("GET"));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
    }

    @Test
    @DisplayName("Unknown path → 404 Not Found (was 500)")
    void unknownPath_returns404() {
        ResponseEntity<ApiResponse<Void>> response =
                handler().handleNoResourceFound(new NoResourceFoundException(HttpMethod.GET, "/api/v1/nope", ""));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
