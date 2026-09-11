package com.lawfirm.erp.rbac.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "modules")
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Module parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Module> subModules = new ArrayList<>();

    @Column(nullable = false)
    private Integer level = 0;

    @Column(nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(length = 100)
    private String icon;

    @Column(length = 200)
    private String path;

    @Column(name = "is_system", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isSystem = false;
}