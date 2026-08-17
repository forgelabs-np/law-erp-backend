package com.lawfirm.erp.modules.scraper.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import com.lawfirm.erp.modules.scraper.enums.HearingSource;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A matched hearing: client case × ingested hearing row, joined on (courtId, caseNoInternal).
 * The notification worker consumes new rows here; `notified` dedupes against repeats.
 */
@Entity
@Table(name = "scraper_hearing_matches", indexes = {
        @Index(name = "idx_shm_client_date", columnList = "clientCaseId, hearingDateAd")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class HearingMatch extends ActiveAuditableEntity {

    @Column(nullable = false)
    private UUID clientCaseId;

    @Column(nullable = false)
    private Integer courtId;

    /** Court-scoped internal id, e.g. "39-081-32030" — kept on the match so it is self-contained. */
    @Column(length = 60, nullable = false)
    private String caseNoInternal;

    @Column(length = 10, nullable = false)
    private String hearingDateBs;

    private LocalDate hearingDateAd;

    @Column(length = 120)
    private String judgeName;

    @Column(length = 200)
    private String orderType;

    /** Full row copy from the source feed — matches are self-contained, no join back needed. */
    @Column(length = 60)
    private String caseNoBs;

    @Column(length = 300)
    private String subject;

    @Column(length = 500)
    private String plaintiff;

    @Column(length = 500)
    private String defendant;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private HearingSource source;

    @Column(nullable = false)
    private LocalDateTime matchedAt = LocalDateTime.now();

    @Column(nullable = false)
    private boolean notified = false;
}
