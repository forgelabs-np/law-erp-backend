package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MatterPartyResponse {

    private UUID id;

    private UUID matterId;

    private String matterNumber;

    private String fullName;

    private String mobileNo;

    private String email;

    private String address;

    private UUID clientId;

    private boolean isOurClient;

    /**
     * Compatibility alias: the field is serialized as {@code ourClient} (Jackson derives that
     * name from Lombok's getter), while the API documentation and the v2 Postman collection
     * use {@code isOurClient}. Both keys carry the same value until the contract is settled.
     */
    @JsonProperty("isOurClient")
    public boolean isOurClientFlag() {
        return isOurClient;
    }

    private String notes;

    private LocalDateTime createdAt;
}
