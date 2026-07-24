package com.lawfirm.erp.common.enums;

public enum AuthStatus {

    /** Credentials valid, no MFA, no password change needed → full JWT issued */
    SUCCESS,

    /** First login — admin set a temp password, user must change it first.
     *  Login returns a passwordChangeToken (limited scope, 10 min).
     *  Client must call POST /auth/change-password before getting full access. */
    PASSWORD_CHANGE_REQUIRED,

    /** MFA enabled but QR code never scanned yet.
     *  Login returns mfaToken + mfaQrCodeUri + mfaSecret.
     *  Client shows QR code screen → user scans → calls POST /auth/mfa/setup/confirm */
    MFA_SETUP_REQUIRED,

    /** MFA enabled and already set up.
     *  Login returns mfaToken only.
     *  Client shows 6-digit input → user types code → calls POST /auth/mfa/validate */
    MFA_REQUIRED
}