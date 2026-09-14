package com.lawfirm.erp.modules.invoice.dto.response;

import com.lawfirm.erp.modules.invoice.enums.InvoiceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class InvoiceResponse {

    private UUID id;
    private UUID firmId;
    private String firmName;
    private String invoiceNumber;
    private InvoiceStatus status;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private BigDecimal subtotal;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal total;
    private String paymentTerms;
    private String notes;
    private UUID createdBy;
    private LocalDateTime createdAt;
}
