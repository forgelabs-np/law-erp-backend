package com.lawfirm.erp.modules.scraper.entity;

import com.lawfirm.erp.entity.base.ActiveAuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A normalized row from the weekly cause list (per-date tables, combined party column,
 * no outcome column). Same normalized schema as DailyHearing so both feeds flow through
 * the same matcher.
 */
@Entity
@Table(name = "scraper_weekly_hearings", uniqueConstraints = {
        @UniqueConstraint(name = "uq_scraper_weekly_key",
                columnNames = {"courtId", "caseNoInternal", "hearingDateBs"})
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WeeklyHearing extends ActiveAuditableEntity {

    @Column(nullable = false)
    private Integer courtId;

    @Column(length = 10, nullable = false)
    private String hearingDateBs;

    private LocalDate hearingDateAd;

    @Column(length = 60)
    private String caseNoBs;

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

    /** Weekly feed has no outcome column — always null here, kept for schema uniformity. */
    @Column(length = 200)
    private String orderType;
}
