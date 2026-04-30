package com.lawfirm.erp.dto.admin.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FeaturePermissionResponse {
    private Long featureId;
    private String featureName;
    private String featureCode;
    private String moduleName;
    private String permissionType;
    private Boolean isAllowed;
}