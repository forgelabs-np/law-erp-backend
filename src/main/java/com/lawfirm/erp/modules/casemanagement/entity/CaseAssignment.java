package com.lawfirm.erp.modules.casemanagement.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import com.lawfirm.erp.modules.casemanagement.enums.AssignmentRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "case_assignments", indexes = {
        @Index(name = "idx_ca_matter", columnList = "matterId"),
        @Index(name = "idx_ca_user", columnList = "userId"),
        @Index(name = "idx_ca_firm_matter", columnList = "firmId, matterId"),
        @Index(name = "idx_ca_firm_user", columnList = "firmId, userId"),
        @Index(name = "idx_ca_unique", columnList = "matterId, userId, assignmentRole", unique = true)
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CaseAssignment extends ActiveAuditableEntity {

    @Column(nullable = false)
    private UUID firmId;

    @Column(nullable = false)
    private UUID matterId;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private AssignmentRole assignmentRole;
}
