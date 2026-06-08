package com.lawfirm.erp.rbac.entity;

import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import com.lawfirm.erp.firm.entity.Firm;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "roles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"role_code", "firm_id"}))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Role extends ActiveAuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firm_id")
    private Firm firm;

    @Column(nullable = false)
    private String roleName;

    @Column(nullable = false)
    private String roleCode;

    @Column(name = "is_system")
    private Boolean isSystem = false;

    @Column(name = "parent_role_id")
    private UUID parentRoleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_to")
    private UserType applicableTo;

    private String description;

    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<RolePermission> permissions = new ArrayList<>();
}