package com.lawfirm.erp.modules.scraper.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A normalized row from the daily cause list (per-judge tables). Upsert keyed on
 * (courtId, caseNoInternal, hearingDateBs) — re-scraping the same day replaces, never duplicates.
 */
@Entity
@Table(name = "scraper_daily_hearings", uniqueConstraints = {
        @UniqueConstraint(name = "uq_scraper_daily_key",
                columnNames = {"courtId", "caseNoInternal", "hearingDateBs"})
}, indexes = {
        @Index(name = "idx_sdh_internal_date", columnList = "caseNoInternal, hearingDateBs")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DailyHearing extends ActiveAuditableEntity {

    @Column(nullable = false)
    private Integer courtId;

    /** Hearing date in BS, verbatim from the site, e.g. "2083-05-01". */
    @Column(length = 10, nullable = false)
    private String hearingDateBs;

    /** Converted AD hearing date for sorting/filtering. */
    private LocalDate hearingDateAd;

    /** Court-visible number, e.g. "081-C4-3827". */
    @Column(length = 60)
    private String caseNoBs;

    /** Court-scoped internal id, e.g. "39-081-32030". */
    @Column(length = 60, nullable = false)
    private String caseNoInternal;

    @Column(length = 120)
    private String judgeName;

    @Column(length = 300)
    private String subject;

    @Column(length = 500)
    private String plaintiff;

    @Column(length = 500)
    private String defendant;

    /** आदेश फैसलाको किसिम — the daily list's outcome/order column. */
    @Column(length = 200)
    private String orderType;
}
