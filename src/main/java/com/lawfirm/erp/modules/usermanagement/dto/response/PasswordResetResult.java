package com.lawfirm.erp.modules.usermanagement.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * What an admin-issued password reset produced.
 *
 * <p>{@code temporaryPassword} is filled in only when the administrator did not choose one (the
 * UI's reset is a confirmation, not a form). It is returned once so the admin can hand it over
 * out of band — the notice e-mail deliberately carries no password — and the holder must rotate
 * it on first login. When the admin did choose the password it is not echoed back.
 */
@Data
@Builder
public class PasswordResetResult {

    private String username;

    /** True when the password was generated rather than supplied. */
    private boolean generated;

    private String temporaryPassword;

    private boolean mustChangePassword;
}
