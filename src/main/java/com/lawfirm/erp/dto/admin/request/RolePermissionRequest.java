package com.lawfirm.erp.dto.admin.request;

import lombok.Data;
import java.util.List;

@Data
public class RolePermissionRequest {
    private Long roleId;
    private List<Long> permissionIds;
}
