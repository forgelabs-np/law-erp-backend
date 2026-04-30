package com.lawfirm.erp.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "role_features", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"role_id", "feature_id", "permission_type"})
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RoleFeature extends ActiveAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "feature_id", nullable = false)
    private Long featureId;

    @Column(name = "permission_type")
    private String permissionType = "VIEW";

    @Column(name = "is_allowed")
    private Boolean isAllowed = true;
}