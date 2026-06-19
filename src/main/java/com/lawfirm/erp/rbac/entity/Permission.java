package com.lawfirm.erp.rbac.entity;

import com.lawfirm.erp.common.enums.PermissionAction;
import com.lawfirm.erp.common.enums.PermissionScope;
import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "permissions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"code"})
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Permission extends ActiveAuditableEntity {

    @Column(nullable = false)
    private String moduleCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PermissionAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PermissionScope scope = PermissionScope.TENANT;

    @Column(nullable = false, unique = true, length = 100)
    private String code;  // Format: MODULE:ACTION (e.g., "CASE_MANAGEMENT:VIEW")

    @Column(length = 200)
    private String description;
}