package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class CreateMatterRequest {

    @NotNull(message = "Matter type is required")
    private MatterType matterType;

    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "Originating court level is required")
    private CourtLevel originatingCourtLevel;

    @NotBlank(message = "Court name is required")
    private String courtName;

    /** The court's official number — often unknown at filing, nullable. */
    private String courtCaseNumber;

    private LocalDate filingDate;

    private UUID assignedPartnerId;

    /**
     * The client this matter is for. Optional — but once set it is what the client
     * portal shows as "my case" and what OWN-scope filtering keys on.
     */
    private UUID clientUserId;

    /** Optional override for the denormalized client name; defaults to the client's full name. */
    private String clientName;

    private UUID advocateId;

    private String description;

    @Valid
    private List<PartyEntryRequest> parties = new ArrayList<>();
}
