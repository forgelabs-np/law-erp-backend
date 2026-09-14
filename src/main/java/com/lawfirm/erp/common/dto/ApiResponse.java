package com.lawfirm.erp.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class ApiResponse<T> implements Serializable {
    private Boolean success;
    private String message;
    private Integer responseCode;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .responseCode(0)
                .message("")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .responseCode(0)
                .message(message)
                .data(data)
                .build();
    }
}
