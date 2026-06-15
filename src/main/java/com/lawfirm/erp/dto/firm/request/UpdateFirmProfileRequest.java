package com.lawfirm.erp.dto.firm.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateFirmProfileRequest {
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @Email(message = "Invalid email format")
    private String email;

    @Size(min = 10, max = 15, message = "Phone must be between 10 and 15 digits")
    private String phone;

    private String address;

    private String jurisdiction;

    private String logoUrl;
}