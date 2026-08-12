package com.lawfirm.erp.modules.casemanagement.dto.response;

import com.lawfirm.erp.modules.casemanagement.enums.PartyRepresentation;
import com.lawfirm.erp.modules.casemanagement.enums.PartyType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CourtCaseRoleResponse {

    private UUID id;

    private UUID matterPartyId;

    private String fullName;

    private UUID courtCaseId;

    private PartyType roleType;

    private PartyRepresentation representation;

    private UUID advocateId;
}
