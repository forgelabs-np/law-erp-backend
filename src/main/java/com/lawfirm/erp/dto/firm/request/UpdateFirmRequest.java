package com.lawfirm.erp.dto.firm.request;

import com.lawfirm.erp.common.enums.FirmType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateFirmRequest {

    @Size(min = 2, max = 100, message = "Firm name must be between 2 and 100 characters")
    private String name;

    private FirmType firmType;

    @Email(message = "Invalid email format")
    private String email;

    private String phone;
    private String address;
    private String jurisdiction;
    private String logoUrl;

    private String adminFullName;

    @Email(message = "Invalid email format")
    private String adminEmail;

    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")
    private String adminMobileNo;

    private String lawFirmCode;
    private String adminUsername;
    private String adminPassword;
}
