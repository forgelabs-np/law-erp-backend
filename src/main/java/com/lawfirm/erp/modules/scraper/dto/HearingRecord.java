package com.lawfirm.erp.modules.scraper.dto;

import com.lawfirm.erp.modules.scraper.enums.HearingSource;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class HearingRecord {

    private Integer courtId;

    private String hearingDateBs;

    private LocalDate hearingDateAd;

    private String caseNoBs;

    private String caseNoInternal;

    private String bench;

    private String serialNo;

    private String judgeName;

    private String subject;

    private String plaintiff;

    private String defendant;

    private String orderType;

    private HearingSource source;
}
