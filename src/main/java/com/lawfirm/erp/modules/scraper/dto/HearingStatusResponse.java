package com.lawfirm.erp.modules.scraper.dto;

import com.lawfirm.erp.modules.scraper.enums.HearingSource;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class HearingStatusResponse {

    private String caseNoInternal;
    private String caseNoBs;
    private Integer courtId;
    private String courtName;
    private List<Hearing> upcoming;
    private List<Hearing> history;

    @Getter
    @Builder
    public static class Hearing {
        private LocalDate hearingDateAd;
        private String hearingDateBs;
        private String judgeName;
        private String subject;
        private String orderType;
        private HearingSource source;
    }
}
