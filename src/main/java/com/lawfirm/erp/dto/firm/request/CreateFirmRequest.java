package com.lawfirm.erp.dto.firm.request;

import com.lawfirm.erp.common.enums.FirmType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateFirmRequest {

    // Firm details
    @NotBlank(message = "Law firm code is required")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Law firm code must be uppercase letters, numbers, and underscores only")
    @Size(min = 3, max = 30)
    private String lawFirmCode;

    @NotBlank(message = "Firm name is required")
    @Size(min = 2, max = 100)
    private String name;

    private FirmType firmType = FirmType.FIRM;

    @Email
    private String email;

    private String phone;
    private String address;
    private String jurisdiction;

    // Firm Admin credentials
    @NotBlank(message = "Admin username is required")
    @Size(min = 3, max = 50)
    private String adminUsername;

    @NotBlank(message = "Admin email is required")
    @Email
    private String adminEmail;

    @NotBlank(message = "Admin mobile number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")
    private String adminMobileNo;

    @NotBlank(message = "Admin password is required")
    @Size(min = 6, max = 50)
    private String adminPassword;

    @NotBlank(message = "Admin full name is required")
    private String adminFullName;
}