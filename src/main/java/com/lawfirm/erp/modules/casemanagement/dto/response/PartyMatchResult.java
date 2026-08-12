package com.lawfirm.erp.modules.casemanagement.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PartyMatchResult {

    @Data
    @Builder
    public static class Match {
        private String sourceType;    // CLIENT | MATTER_PARTY
        private UUID sourceId;
        private String caseNumber;    // matter number when source is a matter party
        private String fullName;
        private String mobileNo;
        private String email;
        private String confidence;    // HIGH | MEDIUM | LOW
    }
}
