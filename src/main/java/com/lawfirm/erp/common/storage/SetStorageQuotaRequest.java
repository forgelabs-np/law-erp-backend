package com.lawfirm.erp.common.storage;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetStorageQuotaRequest {

    @NotNull(message = "quotaBytes is required")
    @PositiveOrZero(message = "quotaBytes cannot be negative")
    private Long quotaBytes;
}
