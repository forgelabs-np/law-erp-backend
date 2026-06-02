package com.lawfirm.erp.rbac.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "role_permissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "permission_id"}))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RolePermission extends ActiveAuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;
}