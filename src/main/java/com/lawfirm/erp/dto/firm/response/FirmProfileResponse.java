package com.lawfirm.erp.dto.firm.response;

import com.lawfirm.erp.common.enums.FirmStatus;
import com.lawfirm.erp.common.enums.FirmType;
import com.lawfirm.erp.common.enums.PlanTier;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class FirmProfileResponse {
    private UUID id;
    private String lawFirmCode;
    private String name;
    private FirmType firmType;
    private FirmStatus status;
    private PlanTier planTier;
    private String email;
    private String phone;
    private String address;
    private String jurisdiction;
    private String logoUrl;
    private Integer maxEmployees;
    private Integer currentEmployeeCount;
    private Integer currentCustomerCount;
    private LocalDateTime createdAt;
}