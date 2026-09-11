package com.lawfirm.erp.dto.firm.response;

import com.lawfirm.erp.common.enums.UserType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class EmployeeResponse {

    private UUID id;
    private String username;
    private String email;
    private String mobileNo;
    private String fullName;
    private UserType userType;
    private Boolean isActive;
    private UUID roleId;
    private String roleName;
    private String roleCode;
    private String employeeCode;
    private String designation;
    private String barCouncilNo;
    private String specialization;
    private LocalDate joiningDate;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String notes;
    private LocalDateTime createdAt;
}