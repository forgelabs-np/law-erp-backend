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

@Entity
@Table(name = "case_parties", indexes = {
        @Index(name = "idx_cp_case", columnList = "caseId"),
        @Index(name = "idx_cp_client", columnList = "clientId"),
        @Index(name = "idx_cp_name", columnList = "fullName")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CaseParty extends ActiveAuditableEntity {

    @Column(nullable = false)
    private UUID firmId;

    @Column(nullable = false)
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PartyType partyType;

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private PartyRepresentation representation;

    @Column(length = 150, nullable = false)
    private String fullName;

    @Column(length = 20)
    private String mobileNo;

    @Column(length = 100)
    private String email;

    @Column(length = 200)
    private String address;

    private UUID clientId;

    @Column(name = "is_our_client")
    private boolean isOurClient;

    private UUID advocateId;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
