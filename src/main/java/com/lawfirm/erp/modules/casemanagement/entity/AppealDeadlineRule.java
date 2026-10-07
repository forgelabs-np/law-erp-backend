package com.lawfirm.erp.modules.casemanagement.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "appeal_deadline_rules", uniqueConstraints = {
        @UniqueConstraint(name = "uk_adr_rule",
                columnNames = {"courtLevelAppealedFrom", "matterType", "partyIsState"})
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AppealDeadlineRule extends ActiveAuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private CourtLevel courtLevelAppealedFrom;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private MatterType matterType;

    @Column(name = "party_is_state", nullable = false)
    private boolean partyIsState;

    @Column(nullable = false)
    private int days;

    @Column(nullable = false)
    private int extensionDays;

    @Column(name = "requires_leave")
    private Boolean requiresLeave;

    @Column(length = 200)
    private String description;
}
