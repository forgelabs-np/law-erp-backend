package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lawfirm.erp.modules.casemanagement.enums.PartyRepresentation;
import com.lawfirm.erp.modules.casemanagement.enums.PartyType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PartyResponse {
    private UUID id;
    private UUID caseId;
    private String caseNumber;
    private String caseTitle;
    private PartyType partyType;
    private PartyRepresentation representation;
    private String fullName;
    private String mobileNo;
    private String email;
    private String address;
    private UUID clientId;
    private boolean isOurClient;

    /** Compatibility alias — see {@link MatterPartyResponse#isOurClientFlag()}. */
    @JsonProperty("isOurClient")
    public boolean isOurClientFlag() {
        return isOurClient;
    }

    private UUID advocateId;
    private String notes;
}
