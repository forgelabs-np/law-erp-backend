package com.lawfirm.erp.modules.casemanagement.dto.request;

import com.lawfirm.erp.modules.casemanagement.enums.CourtCaseStage;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateCourtCaseStageRequest {

    @NotNull(message = "Stage is required")
    private CourtCaseStage stage;
}
