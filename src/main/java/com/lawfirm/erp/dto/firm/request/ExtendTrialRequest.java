package com.lawfirm.erp.dto.firm.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ExtendTrialRequest {
    @Min(value = 1, message = "additionalDays must be at least 1")
    private int additionalDays;
}
