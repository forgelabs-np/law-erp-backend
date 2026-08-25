package com.lawfirm.erp.modules.invoice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreateInvoiceRequest {

    @NotNull(message = "Firm ID is required")
    private UUID firmId;

    @NotNull(message = "Issue date is required")
    private LocalDate issueDate;

    @NotNull(message = "Due date is required")
    private LocalDate dueDate;

    private BigDecimal taxRate;

    private String paymentTerms;

    private String notes;

    @NotEmpty(message = "At least one line item is required")
    @Valid
    private List<InvoiceItemRequest> items;
}
