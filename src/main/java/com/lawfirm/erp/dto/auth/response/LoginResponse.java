package com.lawfirm.erp.dto.auth.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lawfirm.erp.common.enums.AuthStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {

    private AuthStatus status;

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;

    private String mfaToken;

    private String mfaQrCodeUri;
    private String mfaManualKey;

    private String passwordChangeToken;

}