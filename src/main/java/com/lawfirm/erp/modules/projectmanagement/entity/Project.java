package com.lawfirm.erp.modules.projectmanagement.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "projects", indexes = {
        @Index(name = "idx_proj_firm_status", columnList = "firmId, status"),
        @Index(name = "idx_proj_firm_code", columnList = "firmId, projectCode", unique = true),
        @Index(name = "idx_proj_client", columnList = "clientUserId")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Project extends ActiveAuditableEntity {

    @Column(nullable = false)
    private UUID firmId;

    private UUID clientUserId;

    @Column(name = "client_name", nullable = false, length = 200)
    private String clientName;

    @Column(name = "project_code", nullable = false, length = 30, updatable = false)
    private String projectCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    private LocalDate startDate;

    private LocalDate targetEndDate;

    @Column(nullable = false)
    private UUID ownerId;
}
