package com.lawfirm.erp.dto.firm.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;


@Data
public class UpdateEmployeeRequest {
    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")
    private String mobileNo;

    @Size(min = 2, max = 100)
    private String fullName;

    @Size(max = 100)
    private String designation;

    private UUID departmentId;

    @Size(max = 50)
    private String barCouncilNo;

    @Size(max = 100)
    private String specialization;

    private LocalDate joiningDate;

    @Size(max = 100)
    private String emergencyContactName;

    @Pattern(regexp = "^[0-9]{10}$", message = "Emergency contact must be 10 digits")
    private String emergencyContactPhone;

    @Size(max = 500)
    private String notes;
}