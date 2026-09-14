package com.lawfirm.erp.modules.invoice.service;

import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.modules.invoice.dto.request.CreateInvoiceRequest;
import com.lawfirm.erp.modules.invoice.dto.request.UpdateInvoiceRequest;
import com.lawfirm.erp.modules.invoice.dto.request.UpdateInvoiceStatusRequest;
import com.lawfirm.erp.modules.invoice.dto.response.InvoiceDetailResponse;
import com.lawfirm.erp.modules.invoice.dto.response.InvoiceResponse;
import com.lawfirm.erp.modules.invoice.enums.InvoiceStatus;

import java.util.UUID;

public interface InvoiceService {

    PagedResponse<InvoiceResponse> listInvoices(String search, InvoiceStatus status,
                                                  UUID firmId, int page, int size);

    InvoiceDetailResponse getInvoice(UUID id);

    InvoiceResponse createInvoice(CreateInvoiceRequest request);

    InvoiceResponse updateInvoice(UUID id, UpdateInvoiceRequest request);

    void deleteInvoice(UUID id);

    InvoiceResponse updateStatus(UUID id, UpdateInvoiceStatusRequest request);

    byte[] generatePdf(UUID id);

    void sendInvoice(UUID id);
}
