package com.lawfirm.erp.modules.casemanagement.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PartyMatchResult {
    private int partyIndex;

    @Builder.Default
    private List<Match> matches = List.of();

    @Data
    @Builder
    public static class Match {
        private String sourceType; // CLIENT or CASE_PARTY
        private UUID sourceId;
        private String caseNumber; // populated for CASE_PARTY matches
        private String fullName;
        private String mobileNo;
        private String email;
        private String confidence; // HIGH, MEDIUM, LOW
    }
}
