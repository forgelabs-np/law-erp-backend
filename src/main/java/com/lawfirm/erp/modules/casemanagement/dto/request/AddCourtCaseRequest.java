package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.casemanagement.enums.RelationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class AddCourtCaseRequest {

    @NotNull(message = "Relation type is required")
    private RelationType relationType;

    @NotNull(message = "Court level is required")
    private CourtLevel courtLevel;

    @NotBlank(message = "Court name is required")
    private String courtName;

    private String courtCaseNumber;

    private LocalDate filingDate;

    private UUID advocateId;

    private UUID judgeNameId;

    private String judgeName;

    /** Defaults to the matter's current leaf when null. */
    private UUID parentCourtCaseId;

    private boolean partyIsState;

    @Valid
    private List<PartyRoleRequest> roles = new ArrayList<>();
}
