package com.lawfirm.erp.common.constant;

public final class AuthConstants {

    private AuthConstants() {}

    public static final String LOGIN_SUMMARY = "Internal user login";
    public static final String LOGIN_DESCRIPTION = "Authenticate internal firm users with username and password";

    public static final String CLIENT_LOGIN_SUMMARY = "Client login";
    public static final String CLIENT_LOGIN_DESCRIPTION = "Authenticate client users with mobile number and password";

    public static final String REFRESH_TOKEN_SUMMARY = "Refresh access token";
    public static final String REFRESH_TOKEN_DESCRIPTION = "Get new access token using refresh token";

    public static final String MFA_SETUP_CONFIRM_SUMMARY = "Confirm MFA setup";
    public static final String MFA_SETUP_CONFIRM_DESCRIPTION = "Confirm MFA setup after scanning QR code with TOTP code";

    public static final String MFA_VALIDATE_SUMMARY = "Validate MFA code";
    public static final String MFA_VALIDATE_DESCRIPTION = "Validate TOTP code during login when MFA is required";

    public static final String CHANGE_PASSWORD_SUMMARY = "Change password";
    public static final String CHANGE_PASSWORD_DESCRIPTION = "Change password on first login with temporary password";
}
