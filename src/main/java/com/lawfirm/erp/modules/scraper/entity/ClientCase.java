package com.lawfirm.erp.modules.scraper.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import com.lawfirm.erp.modules.scraper.enums.ClientCaseStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A client's case registered for hearing tracking. The scraper derives "which courts to
 * hit today" from the distinct courtIds of ACTIVE rows here — never from a hardcoded list.
 */
@Entity
@Table(name = "scraper_client_cases", indexes = {
        @Index(name = "idx_scc_court_status", columnList = "courtId, caseStatus"),
        @Index(name = "idx_scc_internal", columnList = "caseNoInternal", unique = true)
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClientCase extends ActiveAuditableEntity {

    /** Our internal client id (no FK — decoupled from the firm module). */
    private UUID clientId;

    /** Court this case is filed at (matches the site URL segment). */
    @Column(nullable = false)
    private Integer courtId;

    /** Court-visible case number, e.g. "०८१-C४-३८२७". */
    @Column(length = 60)
    private String caseNoBs;

    /** Court-scoped internal id, e.g. "39-081-32030" (the (…-…-…) form on the site). */
    @Column(length = 60, nullable = false)
    private String caseNoInternal;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private ClientCaseStatus caseStatus = ClientCaseStatus.ACTIVE;
}
