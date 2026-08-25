package com.lawfirm.erp.modules.invoice.dto.response;

import com.lawfirm.erp.modules.invoice.enums.InvoiceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class InvoiceDetailResponse {

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
    private List<InvoiceItemResponse> items;

    @Data
    @Builder
    public static class InvoiceItemResponse {
        private UUID id;
        private String description;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
        private int sortOrder;
    }
}
