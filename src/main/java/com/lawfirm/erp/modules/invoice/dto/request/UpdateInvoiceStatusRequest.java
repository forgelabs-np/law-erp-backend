package com.lawfirm.erp.modules.invoice.dto.request;

import com.lawfirm.erp.modules.invoice.enums.InvoiceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateInvoiceStatusRequest {

    @NotNull(message = "Status is required")
    private InvoiceStatus status;
}
