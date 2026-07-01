package com.lawfirm.erp.firm.entity;

import com.lawfirm.erp.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "firm_module_configs", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"firm_id", "module_code"})
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FirmModuleConfig extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firm_id", nullable = false)
    private Firm firm;

    @Column(name = "module_code", nullable = false, length = 50)
    private String moduleCode;

    @Column(name = "max_file_size_mb")
    private Integer maxFileSizeMb = 10;

    @Column(name = "allowed_file_types", columnDefinition = "TEXT")
    private String allowedFileTypes; // JSON array: ["pdf", "doc", "docx"]

    @Column(name = "custom_settings", columnDefinition = "TEXT")
    private String customSettings; // JSON object for future use
}