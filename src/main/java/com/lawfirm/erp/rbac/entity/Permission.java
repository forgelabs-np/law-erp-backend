package com.lawfirm.erp.rbac.entity;

import com.lawfirm.erp.common.enums.ModuleCode;
import com.lawfirm.erp.common.enums.PermissionAction;
import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Permission extends ActiveAuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModuleCode module;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PermissionAction action;

    @Column(unique = true, nullable = false)
    private String code;                 // "CASE_MANAGEMENT:VIEW"

    private String description;
}