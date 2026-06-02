package com.lawfirm.erp.dto.auth.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class RegisterResponse {
    private UUID userId;
    private String message;
}