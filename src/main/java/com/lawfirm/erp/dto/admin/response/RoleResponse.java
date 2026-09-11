package com.lawfirm.erp.dto.admin.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RoleResponse {
    private UUID id;
    private String name;
    private String code;
    private String description;
    private Boolean isSystem;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PermissionResponse> permissions;

    // ── User association ───────────────────────────────────────────────────
    private Integer userCount;          // How many users hold this role
    private List<String> assignedUserNames; // User full names assigned to this role
}