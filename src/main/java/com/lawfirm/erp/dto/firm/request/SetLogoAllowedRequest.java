package com.lawfirm.erp.dto.firm.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetLogoAllowedRequest {

    @NotNull(message = "logoAllowed is required")
    private Boolean logoAllowed;
}
