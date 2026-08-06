package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.lawfirm.erp.modules.casemanagement.enums.PartyRepresentation;
import com.lawfirm.erp.modules.casemanagement.enums.PartyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AddPartyRequest {

    @NotNull(message = "Party type is required")
    private PartyType partyType;

    private PartyRepresentation representation;

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String mobileNo;
    private String email;
    private String address;
    private UUID clientId;
    private boolean isOurClient;
    private UUID advocateId;
    private String notes;
}
