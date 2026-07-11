package com.lawfirm.erp.dto.firm.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateEmployeeRequest {

    // ── Auth fields (go to users table) ──────────────────────────────────────
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
            message = "Username can only contain letters, numbers, dots, underscores and hyphens")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")
    private String mobileNo;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 50)
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100)
    private String fullName;

    @NotNull(message = "Role is required")
    private UUID roleId;   // Must be a firm-scoped role (isSystem = false)

    // ── Profile fields (go to employee_profiles table)
    @Size(max = 100)
    private String designation;      // "Senior Advocate", "Paralegal" etc.

    private UUID departmentId;       // Optional — link to departments table

    @Size(max = 50)
    private String barCouncilNo;     // Required for advocates, optional for others

    @Size(max = 100)
    private String specialization;   // "Criminal Law", "Civil Law" etc.

    private LocalDate joiningDate;   // Defaults to today if not provided

    @Size(max = 100)
    private String emergencyContactName;

    @Pattern(regexp = "^[0-9]{10}$", message = "Emergency contact must be 10 digits")
    private String emergencyContactPhone;

    @Size(max = 500)
    private String notes;
}