package com.lawfirm.erp.dto.auth.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lawfirm.erp.common.enums.AuthStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {

    /**
     * Tells the frontend what to do next.
     *
     * SUCCESS                  → store accessToken, proceed to app
     * PASSWORD_CHANGE_REQUIRED → show change-password screen, use passwordChangeToken
     * MFA_SETUP_REQUIRED       → show QR code (from mfaQrCodeUri), use mfaToken
     * MFA_REQUIRED             → show 6-digit input screen, use mfaToken
     */
    private AuthStatus status;

    // ── Present only when status = SUCCESS ────────────────────────────────
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;

    // ── Present only when status = MFA_SETUP_REQUIRED or MFA_REQUIRED ────
    // Short-lived token (10 min), valid only for /auth/mfa/* endpoints
    private String mfaToken;

    // ── Present only when status = MFA_SETUP_REQUIRED ────────────────────
    // Pass to a QR code library on frontend (e.g. qrcode.js)
    private String mfaQrCodeUri;
    // Show this as fallback for manual entry in authenticator app
    private String mfaManualKey;

    // ── Present only when status = PASSWORD_CHANGE_REQUIRED ──────────────
    // Short-lived token (10 min), valid only for /auth/change-password
    private String passwordChangeToken;

}