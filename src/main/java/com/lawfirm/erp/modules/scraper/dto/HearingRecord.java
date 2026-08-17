package com.lawfirm.erp.modules.scraper.dto;

import com.lawfirm.erp.modules.scraper.enums.HearingSource;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

// Normalized scrape output produced by both table parsers and upserted by ingestion.
@Getter
@Setter
@Builder
public class HearingRecord {

    private Integer courtId;

    /** Hearing date in BS (Arabic digits), e.g. "2083-05-01". */
    private String hearingDateBs;

    /** Converted AD date, when the BS date parses. */
    private LocalDate hearingDateAd;

    /** Court-visible number, e.g. "081-C4-3827". */
    private String caseNoBs;

    /** Court-scoped internal id, e.g. "39-081-32030". */
    private String caseNoInternal;

    /** इजलाश — bench number, daily feed only (weekly has no benches). */
    private String bench;

    /** क्र. स. — row serial within the bench/date group. */
    private String serialNo;

    private String judgeName;

    private String subject;

    private String plaintiff;

    private String defendant;

    /** Nullable — the weekly feed has no outcome column. */
    private String orderType;

    private HearingSource source;
}
