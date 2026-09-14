package com.lawfirm.erp.dto.auth.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class MfaResetRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    private String reason;
}
