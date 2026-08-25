package com.lawfirm.erp.modules.invoice.mapper;

import com.lawfirm.erp.modules.invoice.dto.response.InvoiceDetailResponse;
import com.lawfirm.erp.modules.invoice.dto.response.InvoiceResponse;
import com.lawfirm.erp.modules.invoice.entity.Invoice;
import com.lawfirm.erp.modules.invoice.entity.InvoiceItem;
import com.lawfirm.erp.firm.entity.Firm;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InvoiceMapper {

    public InvoiceResponse toResponse(Invoice invoice, Firm firm) {
        return InvoiceResponse.builder()
                .id(invoice.getId())
                .firmId(invoice.getFirmId())
                .firmName(firm != null ? firm.getName() : null)
                .invoiceNumber(invoice.getInvoiceNumber())
                .status(invoice.getStatus())
                .issueDate(invoice.getIssueDate())
                .dueDate(invoice.getDueDate())
                .subtotal(invoice.getSubtotal())
                .taxRate(invoice.getTaxRate())
                .taxAmount(invoice.getTaxAmount())
                .total(invoice.getTotal())
                .paymentTerms(invoice.getPaymentTerms())
                .notes(invoice.getNotes())
                .createdBy(invoice.getCreatedBy())
                .createdAt(invoice.getCreatedAt())
                .build();
    }

    public InvoiceDetailResponse toDetailResponse(Invoice invoice, Firm firm, List<InvoiceItem> items) {
        List<InvoiceDetailResponse.InvoiceItemResponse> itemResponses = items.stream()
                .map(this::toItemResponse)
                .toList();

        return InvoiceDetailResponse.builder()
                .id(invoice.getId())
                .firmId(invoice.getFirmId())
                .firmName(firm != null ? firm.getName() : null)
                .invoiceNumber(invoice.getInvoiceNumber())
                .status(invoice.getStatus())
                .issueDate(invoice.getIssueDate())
                .dueDate(invoice.getDueDate())
                .subtotal(invoice.getSubtotal())
                .taxRate(invoice.getTaxRate())
                .taxAmount(invoice.getTaxAmount())
                .total(invoice.getTotal())
                .paymentTerms(invoice.getPaymentTerms())
                .notes(invoice.getNotes())
                .createdBy(invoice.getCreatedBy())
                .createdAt(invoice.getCreatedAt())
                .items(itemResponses)
                .build();
    }

    public InvoiceDetailResponse.InvoiceItemResponse toItemResponse(InvoiceItem item) {
        return InvoiceDetailResponse.InvoiceItemResponse.builder()
                .id(item.getId())
                .description(item.getDescription())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .amount(item.getAmount())
                .sortOrder(item.getSortOrder())
                .build();
    }
}
