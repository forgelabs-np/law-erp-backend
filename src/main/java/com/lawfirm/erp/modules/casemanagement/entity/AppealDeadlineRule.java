package com.lawfirm.erp.modules.casemanagement.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Statutory appeal-window lookup, seeded from the procedure codes.
 * Missing an appeal window is often irreversible — this is a first-class feature.
 */
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

    /**
     * True for further appeals to the Supreme Court: the appeal is not automatic —
     * a leave petition must be filed first (a materially different process from a
     * straight statutory window). The seeded days still bound that petition window.
     *
     * Wrapper type (not primitive): the column was added after the rules table was
     * already seeded, so legacy rows carry NULL — a primitive would break hydration.
     */
    @Column(name = "requires_leave")
    private Boolean requiresLeave;

    @Column(length = 200)
    private String description;
}
