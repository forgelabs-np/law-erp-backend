package com.lawfirm.erp.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@MappedSuperclass
@AllArgsConstructor
@NoArgsConstructor
public abstract class ActiveAuditableEntity extends AuditableEntity{
    @Column(name = "is_active", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean active = true;
}
