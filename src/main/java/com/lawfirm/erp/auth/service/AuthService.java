package com.lawfirm.erp.auth.service;

import com.lawfirm.erp.dto.auth.request.ChangePasswordRequest;
import com.lawfirm.erp.dto.auth.request.ForgotPasswordRequest;
import com.lawfirm.erp.dto.auth.request.LoginRequest;
import com.lawfirm.erp.dto.auth.request.MfaSetupConfirmRequest;
import com.lawfirm.erp.dto.auth.request.MfaValidateRequest;
import com.lawfirm.erp.dto.auth.request.PasswordResetRequest;
import com.lawfirm.erp.dto.auth.response.LoginResponse;

public interface AuthService {

    LoginResponse authenticateInternalUser(LoginRequest request);

    LoginResponse authenticateClient(LoginRequest request);

    LoginResponse refreshToken(String refreshToken);

    LoginResponse confirmMfaSetup(MfaSetupConfirmRequest request);

    LoginResponse validateMfa(MfaValidateRequest request);

    LoginResponse changePassword(ChangePasswordRequest request);

    /**
     * Start self-service recovery. Always behaves the same whether or not the account
     * exists (no enumeration); when it does, a one-time reset link is e-mailed.
     */
    void forgotPassword(ForgotPasswordRequest request);

    /** Redeem the one-time link, set the new password and revoke existing sessions. */
    void resetPasswordWithToken(PasswordResetRequest request);
}
