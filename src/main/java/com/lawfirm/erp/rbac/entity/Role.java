package com.lawfirm.erp.rbac.entity;

import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import com.lawfirm.erp.firm.entity.Firm;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
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

    /**
     * Set when Super Admin last edited this system template's permissions.
     * The boot seeder checks this and skips re-injecting default matrix values
     * into an SA-edited template — without it, a restart would silently
     * resurrect permissions SA removed and re-break the ceiling invariant.
     */
    @Column(name = "last_sa_edit_at")
    private LocalDateTime lastSaEditAt;

    // NEW: Role hierarchy (extends another role)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extends_role_id")
    private Role extendsRole;

    @OneToMany(mappedBy = "extendsRole", fetch = FetchType.LAZY)
    private List<Role> extendedBy = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_to")
    private UserType applicableTo;

    private String description;

    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<RolePermission> permissions = new ArrayList<>();
}