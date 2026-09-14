package com.lawfirm.erp.dto.admin.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RolePermissionResponse {
    private UUID roleId;
    private String roleName;
    private String roleCode;
    private List<PermissionResponse> permissions;

    /**
     * Narrowing-cascade effect (spec §7 — never silent): human-readable list of
     * what this edit also stripped from employee roles. Null when no cascade ran.
     */
    private List<String> cascadeEffect;
}