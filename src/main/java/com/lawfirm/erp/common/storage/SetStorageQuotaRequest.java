package com.lawfirm.erp.common.storage;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

/** Allocates storage to one firm. */
@Getter
@Setter
public class SetStorageQuotaRequest {

    /** Bytes the firm may store. 0 means unlimited. */
    @NotNull(message = "quotaBytes is required")
    @PositiveOrZero(message = "quotaBytes cannot be negative")
    private Long quotaBytes;
}
