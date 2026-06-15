package com.lawfirm.erp.firm.entity;

import com.lawfirm.erp.entity.base.AuditableEntity;
import com.lawfirm.erp.rbac.entity.Module;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "firm_modules", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"firm_id", "module_id"})
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FirmModule extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firm_id", nullable = false)
    private Firm firm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private Module module;

    @Column(name = "is_enabled")
    private Boolean isEnabled = true;

    @Column(name = "enabled_at")
    private LocalDateTime enabledAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;  // For trial/demo

    @Column(name = "is_trial")
    private Boolean isTrial = false;
}