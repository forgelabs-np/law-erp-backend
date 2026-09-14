package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.lawfirm.erp.modules.casemanagement.enums.PartyRepresentation;
import com.lawfirm.erp.modules.casemanagement.enums.PartyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * A party's role in one specific CourtCase. References an existing MatterParty
 * (by id) or carries inline identity to create/link one.
 */
@Data
public class PartyRoleRequest {

    private UUID matterPartyId;

    private String fullName;

    private String mobileNo;

    private String email;

    private UUID clientId;

    @NotNull(message = "Role type is required")
    private PartyType roleType;

    private PartyRepresentation representation = PartyRepresentation.REPRESENTED;

    private UUID advocateId;
}
