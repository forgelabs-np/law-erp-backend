package com.lawfirm.erp.modules.scraper.dto;

import com.lawfirm.erp.modules.scraper.enums.HearingSource;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * The normalized scrape output — both table parsers (daily and weekly) produce this same
 * shape, and ingestion upserts from it.
 */
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

    private String judgeName;

    private String subject;

    private String plaintiff;

    private String defendant;

    /** Nullable — the weekly feed has no outcome column. */
    private String orderType;

    private HearingSource source;
}
