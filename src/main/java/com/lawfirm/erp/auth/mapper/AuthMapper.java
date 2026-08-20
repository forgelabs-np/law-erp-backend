package com.lawfirm.erp.auth.mapper;

import com.lawfirm.erp.common.enums.AuthStatus;
import com.lawfirm.erp.dto.auth.response.LoginResponse;
import com.lawfirm.erp.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public LoginResponse toSuccessResponse(String accessToken, String refreshToken) {
        return LoginResponse.builder()
                .status(AuthStatus.SUCCESS)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(86400000L)
                .build();
    }

    public LoginResponse toMfaSetupResponse(String mfaToken, String qrCodeUri, String manualKey) {
        return LoginResponse.builder()
                .status(AuthStatus.MFA_SETUP_REQUIRED)
                .mfaToken(mfaToken)
                .mfaQrCodeUri(qrCodeUri)
                .mfaManualKey(manualKey)
                .build();
    }

    public LoginResponse toMfaRequiredResponse(String mfaToken) {
        return LoginResponse.builder()
                .status(AuthStatus.MFA_REQUIRED)
                .mfaToken(mfaToken)
                .build();
    }

    public LoginResponse toPasswordChangeRequiredResponse(String passwordChangeToken) {
        return LoginResponse.builder()
                .status(AuthStatus.PASSWORD_CHANGE_REQUIRED)
                .passwordChangeToken(passwordChangeToken)
                .build();
    }
}
