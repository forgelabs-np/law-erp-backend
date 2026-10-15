package com.lawfirm.erp.modules.casemanagement.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "matters", indexes = {
        @Index(name = "idx_matters_firm_type", columnList = "firmId, matterType"),
        @Index(name = "idx_matters_firm_status", columnList = "firmId, status"),
        @Index(name = "idx_matters_firm_client", columnList = "firmId, clientUserId"),
        @Index(name = "idx_matters_number", columnList = "firmId, matterNumber", unique = true)
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Matter extends ActiveAuditableEntity {

    @Column(nullable = false)
    private UUID firmId;

    @Column(length = 30, nullable = false)
    private String matterNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private MatterType matterType;

    @Column(length = 200, nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private MatterStatus status = MatterStatus.ACTIVE;

    private UUID currentCourtCaseId;

    private UUID assignedPartnerId;

    private UUID clientUserId;

    @Column(name = "client_name", length = 200)
    private String clientName;

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private CourtLevel originatingCourtLevel;

    @Column(columnDefinition = "TEXT")
    private String description;
}
