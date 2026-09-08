package com.lawfirm.erp.dto.admin.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Body for template permission endpoints (spec §5). The template is identified
 * by the path variable — no roleId field needed, unlike the firm-role endpoints
 * where the DTO reuses RolePermissionRequest (roleId validated NotNull even
 * though the path variable wins).
 */
@Data
public class TemplatePermissionRequest {

    @NotNull(message = "Permission IDs list is required")
    private List<UUID> permissionIds;
}
