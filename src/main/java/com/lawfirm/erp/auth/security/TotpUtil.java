package com.lawfirm.erp.auth.security;

import org.apache.commons.codec.binary.Base32;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;

import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * TOTP utility — RFC 6238, compatible with Google Authenticator.
 *
 * ADD TO pom.xml:
 *   <dependency>
 *       <groupId>commons-codec</groupId>
 *       <artifactId>commons-codec</artifactId>
 *   </dependency>
 *   (version managed by Spring Boot parent)
 *
 * ADD TO application.properties:
 *   app.name=NepalCRM
 *   app.production=false    ← flip to true in prod
 */
@Component
public class TotpUtil {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final int CLOCK_DRIFT_WINDOWS = 1;   // ±30 seconds tolerance
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final String DEV_BYPASS_CODE = "123456";

    @Value("${app.name:NepalCRM}")
    private String appName;

    @Value("${app.production:false}")
    private boolean isProduction;

    /**
     * Generate a new random TOTP secret.
     * Call once per user when MFA setup starts.
     * Store result in User.mfaSecret.
     */
    public String generateSecret() {
        byte[] bytes = new byte[20];
        new SecureRandom().nextBytes(bytes);
        return new Base32().encodeToString(bytes).replace("=", "");
    }

    /**
     * Build the otpauth:// URI that encodes to a QR code.
     * Google Authenticator scans this to add the account.
     *
     * @param secret    the Base32 secret from User.mfaSecret
     * @param username  the user's login username
     * @param firmCode  the firm code for label clarity
     */
    public String buildQrCodeUri(String secret, String username, String firmCode) {
        String label = encode(appName + ":" + username + " (" + firmCode + ")");
        String issuer = encode(appName);
        return String.format(
                "otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                label, secret, issuer
        );
    }

    /**
     * Format a secret for manual entry (groups of 4 chars).
     * e.g. JBSW Y3DP EHPK 3PXP
     */
    public String formatSecretForDisplay(String secret) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < secret.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append(" ");
            sb.append(secret.charAt(i));
        }
        return sb.toString();
    }

    /**
     * Verify a TOTP code against a stored secret.
     *
     * DEV mode (app.production=false): "123456" always passes.
     * PROD mode: validates RFC 6238 TOTP with ±30s clock drift tolerance.
     *
     * @param secret the Base32 secret from User.mfaSecret
     * @param code   the 6-digit code from the user
     */
    public boolean verify(String secret, String code) {
        if (code == null || code.isBlank()) return false;

        // Dev bypass — never in production
        if (!isProduction && DEV_BYPASS_CODE.equals(code.trim())) {
            return true;
        }

        if (!code.matches("^[0-9]{6}$")) return false;

        try {
            long currentStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
            for (int i = -CLOCK_DRIFT_WINDOWS; i <= CLOCK_DRIFT_WINDOWS; i++) {
                if (generateCode(secret, currentStep + i).equals(code.trim())) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public boolean isProductionMode() {
        return isProduction;
    }

    // ── Private ───────────────────────────────────────────────────────────

    private String generateCode(String secret, long timeStep)
            throws NoSuchAlgorithmException, InvalidKeyException {

        byte[] key = new Base32().decode(secret.toUpperCase());
        byte[] msg = ByteBuffer.allocate(8).putLong(timeStep).array();

        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
        byte[] hash = mac.doFinal(msg);

        int offset = hash[hash.length - 1] & 0x0F;
        int otp = ((hash[offset]     & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                |  (hash[offset + 3] & 0xFF);

        return String.format("%06d", otp % 1_000_000);
    }

    private String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }
}