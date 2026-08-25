package com.lawfirm.erp.modules.invoice.service;

import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.modules.email.service.EmailService;
import com.lawfirm.erp.modules.invoice.dto.request.CreateInvoiceRequest;
import com.lawfirm.erp.modules.invoice.dto.request.InvoiceItemRequest;
import com.lawfirm.erp.modules.invoice.dto.request.UpdateInvoiceRequest;
import com.lawfirm.erp.modules.invoice.dto.request.UpdateInvoiceStatusRequest;
import com.lawfirm.erp.modules.invoice.dto.response.InvoiceDetailResponse;
import com.lawfirm.erp.modules.invoice.dto.response.InvoiceResponse;
import com.lawfirm.erp.modules.invoice.entity.Invoice;
import com.lawfirm.erp.modules.invoice.entity.InvoiceItem;
import com.lawfirm.erp.modules.invoice.enums.InvoiceStatus;
import com.lawfirm.erp.modules.invoice.mapper.InvoiceMapper;
import com.lawfirm.erp.modules.invoice.repository.InvoiceItemRepository;
import com.lawfirm.erp.modules.invoice.repository.InvoiceRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final FirmRepository firmRepository;
    private final UserRepository userRepository;
    private final CurrentUserResolver currentUserResolver;
    private final AuditService auditService;
    private final EmailService emailService;
    private final InvoiceMapper invoiceMapper;
    private final InvoicePdfService invoicePdfService;

    @Override
    public PagedResponse<InvoiceResponse> listInvoices(String search, InvoiceStatus status,
                                                        UUID firmId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Invoice> invoicePage = invoiceRepository.search(search, status, firmId, pageable);

        // Batch-resolve firm names
        List<UUID> firmIds = invoicePage.getContent().stream()
                .map(Invoice::getFirmId).distinct().collect(Collectors.toList());
        java.util.Map<UUID, Firm> firmMap = firmIds.isEmpty() ? java.util.Map.of()
                : firmRepository.findAllById(firmIds).stream()
                        .collect(Collectors.toMap(Firm::getId, f -> f));

        List<InvoiceResponse> content = invoicePage.getContent().stream()
                .map(inv -> invoiceMapper.toResponse(inv, firmMap.get(inv.getFirmId())))
                .collect(Collectors.toList());

        return PagedResponse.of(invoicePage, content);
    }

    @Override
    public InvoiceDetailResponse getInvoice(UUID id) {
        Invoice invoice = findInvoice(id);
        List<InvoiceItem> items = invoiceItemRepository.findByInvoiceIdOrderBySortOrderAsc(id);
        Firm firm = firmRepository.findById(invoice.getFirmId()).orElse(null);
        return invoiceMapper.toDetailResponse(invoice, firm, items);
    }

    @Transactional
    @Override
    public InvoiceResponse createInvoice(CreateInvoiceRequest request) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        if (adminId == null) throw new ForbiddenException("Super Admin context required");

        Firm firm = firmRepository.findById(request.getFirmId())
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found: " + request.getFirmId()));

        Invoice invoice = Invoice.builder()
                .firmId(request.getFirmId())
                .invoiceNumber(generateInvoiceNumber())
                .status(InvoiceStatus.DRAFT)
                .issueDate(request.getIssueDate())
                .dueDate(request.getDueDate())
                .taxRate(request.getTaxRate() != null ? request.getTaxRate() : BigDecimal.ZERO)
                .paymentTerms(request.getPaymentTerms())
                .notes(request.getNotes())
                .createdBy(adminId)
                .build();

        // Build line items
        List<InvoiceItem> items = new ArrayList<>();
        for (int i = 0; i < request.getItems().size(); i++) {
            InvoiceItemRequest itemReq = request.getItems().get(i);
            InvoiceItem item = InvoiceItem.builder()
                    .invoice(invoice)
                    .description(itemReq.getDescription())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .sortOrder(i)
                    .build();
            item.recalculateAmount();
            items.add(item);
        }
        invoice.setItems(items);
        invoice.recalculateTotals();

        invoice = invoiceRepository.save(invoice);

        auditService.log(AuditAction.INVOICE_CREATED, AuditEntity.INVOICE,
                invoice.getId(), "Invoice created: " + invoice.getInvoiceNumber()
                        + " for firm " + firm.getName() + " — total: " + invoice.getTotal());

        log.info("Invoice created: {} for firm {} — total: {}", invoice.getInvoiceNumber(), firm.getName(), invoice.getTotal());
        return invoiceMapper.toResponse(invoice, firm);
    }

    @Transactional
    @Override
    public InvoiceResponse updateInvoice(UUID id, UpdateInvoiceRequest request) {
        Invoice invoice = findInvoice(id);
        validateDraft(invoice);

        if (request.getIssueDate() != null) invoice.setIssueDate(request.getIssueDate());
        if (request.getDueDate() != null) invoice.setDueDate(request.getDueDate());
        if (request.getTaxRate() != null) invoice.setTaxRate(request.getTaxRate());
        if (request.getPaymentTerms() != null) invoice.setPaymentTerms(request.getPaymentTerms());
        if (request.getNotes() != null) invoice.setNotes(request.getNotes());

        // Replace line items if provided
        if (request.getItems() != null) {
            // Remove old items
            invoice.getItems().clear();

            // Add new items
            for (int i = 0; i < request.getItems().size(); i++) {
                InvoiceItemRequest itemReq = request.getItems().get(i);
                InvoiceItem item = InvoiceItem.builder()
                        .invoice(invoice)
                        .description(itemReq.getDescription())
                        .quantity(itemReq.getQuantity())
                        .unitPrice(itemReq.getUnitPrice())
                        .sortOrder(i)
                        .build();
                item.recalculateAmount();
                invoice.getItems().add(item);
            }
        }

        invoice.recalculateTotals();
        invoice = invoiceRepository.save(invoice);

        Firm firm = firmRepository.findById(invoice.getFirmId()).orElse(null);
        auditService.log(AuditAction.INVOICE_UPDATED, AuditEntity.INVOICE,
                invoice.getId(), "Invoice updated: " + invoice.getInvoiceNumber());

        log.info("Invoice updated: {}", invoice.getInvoiceNumber());
        return invoiceMapper.toResponse(invoice, firm);
    }

    @Transactional
    @Override
    public void deleteInvoice(UUID id) {
        Invoice invoice = findInvoice(id);
        validateDraft(invoice);

        invoiceRepository.delete(invoice);

        auditService.log(AuditAction.INVOICE_DELETED, AuditEntity.INVOICE,
                id, "Invoice deleted: " + invoice.getInvoiceNumber());

        log.info("Invoice deleted: {}", invoice.getInvoiceNumber());
    }

    @Transactional
    @Override
    public InvoiceResponse updateStatus(UUID id, UpdateInvoiceStatusRequest request) {
        Invoice invoice = findInvoice(id);
        InvoiceStatus newStatus = request.getStatus();

        if (!invoice.getStatus().canTransitionTo(newStatus)) {
            throw new BusinessRuleException(
                    "Cannot transition from " + invoice.getStatus() + " to " + newStatus);
        }

        invoice.setStatus(newStatus);
        invoice = invoiceRepository.save(invoice);

        Firm firm = firmRepository.findById(invoice.getFirmId()).orElse(null);
        auditService.log(AuditAction.INVOICE_UPDATED, AuditEntity.INVOICE,
                invoice.getId(), "Invoice " + invoice.getInvoiceNumber() + " status → " + newStatus);

        log.info("Invoice {} status changed to {}", invoice.getInvoiceNumber(), newStatus);
        return invoiceMapper.toResponse(invoice, firm);
    }

    @Override
    public byte[] generatePdf(UUID id) {
        Invoice invoice = findInvoice(id);
        List<InvoiceItem> items = invoiceItemRepository.findByInvoiceIdOrderBySortOrderAsc(id);
        Firm firm = firmRepository.findById(invoice.getFirmId())
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));
        return invoicePdfService.generatePdf(invoice, items, firm);
    }

    @Override
    public void sendInvoice(UUID id) {
        Invoice invoice = findInvoice(id);
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            invoice.setStatus(InvoiceStatus.SENT);
            invoice = invoiceRepository.save(invoice);
        }

        Firm firm = firmRepository.findById(invoice.getFirmId())
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found"));

        // Find firm admin email
        User firmAdmin = userRepository.findFirmAdminsByFirmId(firm.getId()).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException("No firm admin found for firm: " + firm.getName()));

        byte[] pdfBytes = generatePdf(id);

        emailService.sendInvoiceEmail(
                firm.getId(),
                firmAdmin.getId(),
                firmAdmin.getEmail(),
                firm.getName(),
                invoice.getInvoiceNumber(),
                invoice.getTotal(),
                pdfBytes
        );

        auditService.log(AuditAction.INVOICE_SENT, AuditEntity.INVOICE,
                invoice.getId(), "Invoice " + invoice.getInvoiceNumber() + " sent to " + firmAdmin.getEmail());

        log.info("Invoice {} sent to {} at {}", invoice.getInvoiceNumber(), firmAdmin.getFullName(), firmAdmin.getEmail());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private Invoice findInvoice(UUID id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));
    }

    private void validateDraft(Invoice invoice) {
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new BusinessRuleException("Only DRAFT invoices can be modified");
        }
    }

    private String generateInvoiceNumber() {
        int year = Year.now().getValue();
        String pattern = "INV-" + year + "-%";

        List<String> existing = invoiceRepository.findMaxInvoiceNumberByPattern(
                pattern, PageRequest.of(0, 1));

        int nextSeq = 1;
        if (!existing.isEmpty()) {
            String last = existing.get(0); // e.g. INV-2025-0015
            String seqPart = last.substring(last.lastIndexOf('-') + 1);
            nextSeq = Integer.parseInt(seqPart) + 1;
        }

        return String.format("INV-%d-%04d", year, nextSeq);
    }
}
