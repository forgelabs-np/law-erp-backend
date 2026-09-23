package com.lawfirm.erp.modules.usermanagement.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * An administrator resetting another user's password.
 *
 * <p>The password is optional. The console's reset is a confirmation dialog — it sends no body at
 * all — so when no password is supplied the service generates a policy-compliant temporary one,
 * returns it to the administrator to hand over, and forces a rotation on the next login. A
 * supplied password has to satisfy the product's single policy (8–50). The field also answers
 * to {@code password} / {@code pwd} / {@code new_password}, so a console dialog that names the
 * field differently still sets the password the administrator typed instead of silently falling
 * back to a generated one.
 */
@Data
public class ResetPasswordRequest {

    @JsonAlias({"password", "pwd", "new_password"})
    @Size(min = 8, max = 50, message = "Password must be 8-50 characters")
    private String newPassword;
}
