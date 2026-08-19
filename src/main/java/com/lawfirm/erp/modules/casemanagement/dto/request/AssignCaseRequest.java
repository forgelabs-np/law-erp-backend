package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.lawfirm.erp.modules.casemanagement.enums.AssignmentRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AssignCaseRequest {

    @NotNull
    private UUID userId;

    @NotNull
    private AssignmentRole assignmentRole;
}
