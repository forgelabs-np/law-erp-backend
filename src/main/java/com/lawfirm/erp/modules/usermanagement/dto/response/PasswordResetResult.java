package com.lawfirm.erp.modules.usermanagement.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PasswordResetResult {

    private String username;

    private boolean generated;

    private String temporaryPassword;

    private boolean mustChangePassword;
}
