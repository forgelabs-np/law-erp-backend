package com.lawfirm.erp.modules.casemanagement.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import com.lawfirm.erp.modules.casemanagement.enums.PartyRepresentation;
import com.lawfirm.erp.modules.casemanagement.enums.PartyType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One row per (MatterParty, CourtCase) pair — the role that actually varies per instance.
 * The defendant who lost at District Court becomes the APPELLANT at High Court.
 */
@Entity
@Table(name = "court_case_roles", indexes = {
        @Index(name = "idx_ccr_court_case", columnList = "courtCaseId"),
        @Index(name = "idx_ccr_party", columnList = "matterPartyId")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CourtCaseRole extends ActiveAuditableEntity {

    @Column(nullable = false)
    private UUID firmId;

    @Column(nullable = false)
    private UUID matterPartyId;

    @Column(nullable = false)
    private UUID courtCaseId;

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private PartyType roleType;

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private PartyRepresentation representation;

    /** Advocate representing this party in THIS court instance. */
    private UUID advocateId;
}
