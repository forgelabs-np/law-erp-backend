package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.lawfirm.erp.modules.casemanagement.enums.CaseStage;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateCaseStageRequest {

    @NotNull(message = "Stage is required")
    private CaseStage stage;
}
