package com.lawfirm.erp.rbac.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "modules", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"code"}),
        @UniqueConstraint(columnNames = {"name"})
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Module extends ActiveAuditableEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(length = 200)
    private String description;

    @Column(nullable = false)
    private Integer displayOrder = 0;

    @Column(length = 100)
    private String icon;

    @Column(length = 200)
    private String path;

    @Column(name = "is_system", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isSystem = false;
}