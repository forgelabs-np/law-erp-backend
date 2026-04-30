package com.lawfirm.erp.dto.admin.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RolePermissionResponse {
    private Long roleId;
    private String roleName;
    private List<PermissionResponse> permissions;
}
