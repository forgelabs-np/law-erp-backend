package com.lawfirm.erp.auth.service;

import com.lawfirm.erp.dto.auth.request.ChangePasswordRequest;
import com.lawfirm.erp.dto.auth.request.LoginRequest;
import com.lawfirm.erp.dto.auth.request.MfaSetupConfirmRequest;
import com.lawfirm.erp.dto.auth.request.MfaValidateRequest;
import com.lawfirm.erp.dto.auth.response.LoginResponse;

public interface AuthService {

    LoginResponse authenticateInternalUser(LoginRequest request);

    LoginResponse authenticateClient(LoginRequest request);

    LoginResponse refreshToken(String refreshToken);

    LoginResponse confirmMfaSetup(MfaSetupConfirmRequest request);

    LoginResponse validateMfa(MfaValidateRequest request);

    LoginResponse changePassword(ChangePasswordRequest request);
}
