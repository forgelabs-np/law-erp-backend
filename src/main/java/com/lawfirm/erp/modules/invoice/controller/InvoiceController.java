package com.lawfirm.erp.modules.invoice.controller;

import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.modules.invoice.dto.request.CreateInvoiceRequest;
import com.lawfirm.erp.modules.invoice.dto.request.UpdateInvoiceRequest;
import com.lawfirm.erp.modules.invoice.dto.request.UpdateInvoiceStatusRequest;
import com.lawfirm.erp.modules.invoice.dto.response.InvoiceDetailResponse;
import com.lawfirm.erp.modules.invoice.dto.response.InvoiceResponse;
import com.lawfirm.erp.modules.invoice.enums.InvoiceStatus;
import com.lawfirm.erp.modules.invoice.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/super-admin/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Invoice Generator", description = "Create, manage, and send invoices to firms")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final ResponseHandler responseHandler;

    @GetMapping
    @Operation(summary = "List all invoices", description = "Paginated list with optional filters for status, firm, and search")
    public ResponseEntity<ApiResponse<PagedResponse<InvoiceResponse>>> listInvoices(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID firmId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responseHandler.ok(
                invoiceService.listInvoices(search, status, firmId, page, size),
                "Invoices fetched successfully");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get invoice detail", description = "Get invoice with all line items")
    public ResponseEntity<ApiResponse<InvoiceDetailResponse>> getInvoice(@PathVariable UUID id) {
        return responseHandler.ok(invoiceService.getInvoice(id), "Invoice fetched successfully");
    }

    @PostMapping
    @Operation(summary = "Create invoice", description = "Create a new invoice with line items. Status starts as DRAFT.")
    public ResponseEntity<ApiResponse<InvoiceResponse>> createInvoice(
            @Valid @RequestBody ApiRequest<CreateInvoiceRequest> request) {
        return responseHandler.ok(
                invoiceService.createInvoice(request.getData()),
                "Invoice created successfully");
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update invoice", description = "Update invoice details and line items (DRAFT only)")
    public ResponseEntity<ApiResponse<InvoiceResponse>> updateInvoice(
            @PathVariable UUID id,
            @Valid @RequestBody ApiRequest<UpdateInvoiceRequest> request) {
        return responseHandler.ok(
                invoiceService.updateInvoice(id, request.getData()),
                "Invoice updated successfully");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete invoice", description = "Delete invoice (DRAFT only)")
    public ResponseEntity<ApiResponse<Void>> deleteInvoice(@PathVariable UUID id) {
        invoiceService.deleteInvoice(id);
        return responseHandler.ok(null, "Invoice deleted successfully");
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update invoice status", description = "Change status with validation (e.g., DRAFT → SENT)")
    public ResponseEntity<ApiResponse<InvoiceResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ApiRequest<UpdateInvoiceStatusRequest> request) {
        return responseHandler.ok(
                invoiceService.updateStatus(id, request.getData()),
                "Invoice status updated successfully");
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Download invoice PDF", description = "Generate and download PDF for the invoice")
    public void downloadPdf(@PathVariable UUID id, HttpServletResponse response) throws IOException {
        byte[] pdfBytes = invoiceService.generatePdf(id);
        InvoiceDetailResponse invoice = invoiceService.getInvoice(id);

        response.setContentType("application/pdf");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + invoice.getInvoiceNumber() + ".pdf\"");
        response.setContentLength(pdfBytes.length);
        response.getOutputStream().write(pdfBytes);
        response.getOutputStream().flush();
    }

    @PostMapping("/{id}/send")
    @Operation(summary = "Send invoice via email", description = "Generate PDF and email it to the firm admin")
    public ResponseEntity<ApiResponse<Void>> sendInvoice(@PathVariable UUID id) {
        invoiceService.sendInvoice(id);
        return responseHandler.ok(null, "Invoice sent successfully");
    }
}
