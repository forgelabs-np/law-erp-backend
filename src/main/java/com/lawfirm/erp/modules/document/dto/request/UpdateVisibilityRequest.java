package com.lawfirm.erp.modules.document.dto.request;

import com.lawfirm.erp.modules.document.enums.DocumentVisibility;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateVisibilityRequest {

    @NotNull(message = "Visibility is required")
    private DocumentVisibility visibility;
}
