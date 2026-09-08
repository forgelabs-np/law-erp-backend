package com.lawfirm.erp.dto.admin.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class RoleRequest {
    private UUID id;

    @NotBlank(message = "Role name is required")
    private String name;

    @NotBlank(message = "Role code is required")
    private String code;

    private String description;

    private Boolean isActive;

    /**
     * Optional base system template for custom roles (lineage + future ceiling
     * semantics). Must reference an active system role (firm IS NULL, isSystem=true),
     * and never SUPER_ADMIN. When omitted, the role simply has no template anchor.
     */
    private UUID parentRoleId;

    private List<UUID> permissionIds;
}