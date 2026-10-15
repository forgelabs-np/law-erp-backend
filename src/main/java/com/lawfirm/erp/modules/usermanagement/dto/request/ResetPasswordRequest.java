package com.lawfirm.erp.modules.usermanagement.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @JsonAlias({"password", "pwd", "new_password"})
    @Size(min = 8, max = 50, message = "Password must be 8-50 characters")
    private String newPassword;
}
