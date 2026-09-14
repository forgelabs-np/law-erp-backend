package com.lawfirm.erp.modules.invoice.dto.request;

import jakarta.validation.Valid;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class UpdateInvoiceRequest {

    private LocalDate issueDate;

    private LocalDate dueDate;

    private BigDecimal taxRate;

    private String paymentTerms;

    private String notes;

    @Valid
    private List<InvoiceItemRequest> items;
}
