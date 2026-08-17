package com.lawfirm.erp.modules.scraper.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CaseDetailResponse {

    private boolean found;
    private Integer courtId;
    private String caseNoBs;
    private String caseNoInternal;
    private String registrationDate;
    private String caseType;
    private String subject;
    private String division;
    private String status;
    private String verdictDate;
    private String verdictJudge;
    private String hearingCount;
    private List<Party> plaintiffs;
    private List<Party> defendants;
    private List<HearingEntry> hearings;
    private List<HearingEntry> tarekhe;

    @Getter
    @Builder
    public static class Party {
        private String name;
        private String address;
    }

    @Getter
    @Builder
    public static class HearingEntry {
        private String dateBs;
        private String type;
        private String division;
        private String judge;
        private String order;
    }
}
