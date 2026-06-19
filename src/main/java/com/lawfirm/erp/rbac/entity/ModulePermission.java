package com.lawfirm.erp.rbac.entity;

import com.lawfirm.erp.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "module_permissions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"module_id", "permission_id"})
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ModulePermission extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private Module module;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;
}