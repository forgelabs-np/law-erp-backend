package com.lawfirm.erp.modules.casemanagement.dto.response;

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

    private String notes;

    private LocalDateTime createdAt;
}
