package com.lawfirm.erp.modules.usermanagement.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * An administrator resetting another user's password.
 *
 * <p>The password is optional. The console's reset is a confirmation dialog — it sends no body at
 * all — so when no password is supplied the service generates a policy-compliant temporary one,
 * returns it to the administrator to hand over, and forces a rotation on the next login. A
 * supplied password has to satisfy the product's single policy (8–50).
 */
@Data
public class ResetPasswordRequest {

    @Size(min = 8, max = 50, message = "Password must be 8-50 characters")
    private String newPassword;
}
