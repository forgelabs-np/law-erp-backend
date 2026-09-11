package com.lawfirm.erp.dto.admin.request;

import lombok.Data;

@Data
public class RoleFeatureRequest {
    private Long featureId;
    private String permissionType;
    private Boolean isAllowed;
}