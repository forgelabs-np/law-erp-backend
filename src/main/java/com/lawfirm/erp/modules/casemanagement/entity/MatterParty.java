package com.lawfirm.erp.modules.casemanagement.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Party identity at Matter level — created once, deduplicated via party matching.
 * Role flips on appeal are modeled in CourtCaseRole, not here.
 */
@Entity
@Table(name = "matter_parties", indexes = {
        @Index(name = "idx_mp_matter", columnList = "matterId"),
        @Index(name = "idx_mp_client", columnList = "clientId"),
        @Index(name = "idx_mp_name", columnList = "fullName")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MatterParty extends ActiveAuditableEntity {

    @Column(nullable = false)
    private UUID firmId;

    @Column(nullable = false)
    private UUID matterId;

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

    @Column(columnDefinition = "TEXT")
    private String notes;
}
