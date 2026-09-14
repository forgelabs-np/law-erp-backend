package com.lawfirm.erp.modules.scraper.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

// One row of the daily cause list. Upsert key: (courtId, caseNoInternal, hearingDateBs).
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
public class DailyHearing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer courtId;

    @Column(length = 10, nullable = false)
    private String hearingDateBs;

    private LocalDate hearingDateAd;

    @Column(length = 60)
    private String caseNoBs;

    @Column(length = 60, nullable = false)
    private String caseNoInternal;

    // इजलाश — bench number, daily feed only.
    @Column(length = 10)
    private String bench;

    // क्र. स. — serial within the bench; Devanagari letters (क, ख) kept as-is.
    @Column(length = 10)
    private String serialNo;

    @Column(length = 120)
    private String judgeName;

    @Column(length = 300)
    private String subject;

    @Column(length = 500)
    private String plaintiff;

    @Column(length = 500)
    private String defendant;

    @Column(length = 200)
    private String orderType;

    private LocalDate scrapedDate;
}
