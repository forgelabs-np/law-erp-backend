package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lawfirm.erp.modules.casemanagement.enums.PartyRepresentation;
import com.lawfirm.erp.modules.casemanagement.enums.PartyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class PartyEntryRequest {

    @NotBlank(message = "Party full name is required")
    private String fullName;

    private String mobileNo;

    private String email;

    private String address;

    private UUID clientId;

    @JsonProperty("isOurClient")
    @JsonAlias("ourClient")
    private boolean isOurClient;

    @NotNull(message = "Party role type is required")
    private PartyType roleType;

    private PartyRepresentation representation = PartyRepresentation.REPRESENTED;

    private UUID advocateId;

    private String notes;
}
