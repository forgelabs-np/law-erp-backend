package com.lawfirm.erp.tenant.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenant_types")
@Getter
@Setter
@NoArgsConstructor
public class TenantType extends ActiveAuditableEntity {

    @Column(unique = true, nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String code;

    private String description;

    public TenantType(String name, String code, String description) {
        this.name = name;
        this.code = code;
        this.description = description;
    }
}