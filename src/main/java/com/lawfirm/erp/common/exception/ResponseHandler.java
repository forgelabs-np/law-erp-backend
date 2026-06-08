package com.lawfirm.erp.common.exception;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.enums.ApiStatus;
import com.lawfirm.erp.common.enums.Message;
import com.lawfirm.erp.common.service.MessageSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ResponseHandler {

    private final MessageSourceService messageSource;

    public <T> ResponseEntity<ApiResponse<T>> ok(T data, Message message, Object... args) {
        ApiResponse<T> response = ApiResponse.<T>builder()
                .success(true)
                .responseCode(ApiStatus.SUCCESS.getCode())
                .message(messageSource.getMessage(message.getCode(), args))
                .data(data)
                .build();
        return ResponseEntity.ok(response);
    }

    public <T> ResponseEntity<ApiResponse<T>> error(String errorMessage, ApiStatus apiStatus, HttpStatus status) {
        ApiResponse<T> response = ApiResponse.<T>builder()
                .success(false)
                .responseCode(apiStatus.getCode())
                .message(errorMessage)
                .build();
        return ResponseEntity.status(status).body(response);
    }

    public <T> ResponseEntity<ApiResponse<T>> ok(T response) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true)
                .responseCode(0)
                .data(response)
                .message("")
                .build());
    }

    public <T> ResponseEntity<ApiResponse<T>> ok(T data, String message) {
        ApiResponse<T> response = ApiResponse.<T>builder()
                .success(true)
                .responseCode(ApiStatus.SUCCESS.getCode())
                .message(message)
                .data(data)
                .build();
        return ResponseEntity.ok(response);
    }

}