package com.lawfirm.erp.common.constant;

public final class AuthConstants {

    private AuthConstants() {}

    public static final String LOGIN_SUMMARY = "Internal user login";
    public static final String LOGIN_DESCRIPTION = "Authenticate internal firm users with username and password";

    public static final String CLIENT_LOGIN_SUMMARY = "Client login";
    public static final String CLIENT_LOGIN_DESCRIPTION = "Authenticate client users with mobile number and password";

    public static final String REFRESH_TOKEN_SUMMARY = "Refresh access token";
    public static final String REFRESH_TOKEN_DESCRIPTION = "Exchange a refresh token for a new token pair. Refresh tokens are single-use: replaying one revokes every refresh token for the account. Password changes, role changes and logout retire them server-side.";

    public static final String MFA_SETUP_CONFIRM_SUMMARY = "Confirm MFA setup";
    public static final String MFA_SETUP_CONFIRM_DESCRIPTION = "Confirm MFA setup after scanning QR code with TOTP code";

    public static final String MFA_VALIDATE_SUMMARY = "Validate MFA code";
    public static final String MFA_VALIDATE_DESCRIPTION = "Validate TOTP code during login when MFA is required";

    public static final String CHANGE_PASSWORD_SUMMARY = "Change password";
    public static final String CHANGE_PASSWORD_DESCRIPTION = "Change password on first login with temporary password";

    public static final String LOGOUT_SUMMARY = "Log out (server-side)";
    public static final String LOGOUT_DESCRIPTION = "Ends every session for the account on every device: bumps the permission version, which immediately invalidates all access tokens and makes every refresh token unusable. No body required.";
}
