package com.lawfirm.erp.dto.firm.request;

import com.lawfirm.erp.common.enums.FirmType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload for {@code PUT /api/v1/super-admin/firms/{firmId}}.
 *
 * <p>Every field is optional: {@code null} (or blank) means "leave as it is", so the console can
 * post only what the operator touched. Note there is deliberately <b>no</b> {@code @NotBlank} —
 * a missing password is what made firm updates impossible before (the edit screen used the create
 * endpoint, which demanded one).
 *
 * <p>{@code lawFirmCode}, {@code adminUsername} and {@code adminPassword} are accepted so the
 * create payload can be replayed unchanged, but they are immutable: posting a <i>different</i>
 * value is rejected with a 400 instead of being silently dropped.
 */
@Data
public class UpdateFirmRequest {

    // ── Firm details ─────────────────────────────────────────────────────
    @Size(min = 2, max = 100, message = "Firm name must be between 2 and 100 characters")
    private String name;

    private FirmType firmType;

    @Email(message = "Invalid email format")
    private String email;

    private String phone;
    private String address;
    private String jurisdiction;
    private String logoUrl;

    // ── Firm Admin contact details (username/password are NOT updatable here) ──
    private String adminFullName;

    @Email(message = "Invalid email format")
    private String adminEmail;

    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")
    private String adminMobileNo;

    // ── Immutable after creation (accepted only so the create body still binds) ──
    private String lawFirmCode;
    private String adminUsername;
    private String adminPassword;
}
