package com.lawfirm.erp.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "modules")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Module extends ActiveAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String code;

    private String description;
    private String icon;
    private Integer displayOrder;
}