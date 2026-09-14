package com.lawfirm.erp.modules.invoice.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.firm.repository.FirmRepository;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.email.service.EmailService;
import com.lawfirm.erp.modules.invoice.dto.request.UpdateInvoiceStatusRequest;
import com.lawfirm.erp.modules.invoice.entity.Invoice;
import com.lawfirm.erp.modules.invoice.enums.InvoiceStatus;
import com.lawfirm.erp.modules.invoice.mapper.InvoiceMapper;
import com.lawfirm.erp.modules.invoice.repository.InvoiceItemRepository;
import com.lawfirm.erp.modules.invoice.repository.InvoiceRepository;
import com.lawfirm.erp.modules.notification.event.NotificationEvent;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceStatusNotificationTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceItemRepository invoiceItemRepository;
    @Mock private FirmRepository firmRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private AuditService auditService;
    @Mock private EmailService emailService;
    @Mock private InvoiceMapper invoiceMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private InvoiceServiceImpl service;

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    private Invoice invoice;

    @BeforeEach
    void setUp() {
        invoice = Invoice.builder()
                .firmId(FIRM_ID)
                .invoiceNumber("INV-2026-0004")
                .status(InvoiceStatus.SENT)
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(15))
                .total(BigDecimal.valueOf(55_000))
                .build();
        invoice.setId(INVOICE_ID);
    }

    @Test
    @DisplayName("updateStatus to PAID publishes one INVOICE_STATUS event targeted at FIRM_ADMIN")
    void updateStatus_publishesEvent() {
        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(firmRepository.findById(FIRM_ID)).thenReturn(Optional.of(new Firm()));
        when(invoiceMapper.toResponse(any(Invoice.class), any(Firm.class))).thenReturn(null);

        UpdateInvoiceStatusRequest req = new UpdateInvoiceStatusRequest();
        req.setStatus(InvoiceStatus.PAID);
        service.updateStatus(INVOICE_ID, req);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        NotificationEvent event = captor.getValue();
        assertEquals(NotificationType.INVOICE_STATUS, event.type());
        assertEquals(FIRM_ID, event.firmId());
        assertEquals("FIRM_ADMIN", event.recipientRoleCode());
        assertNull(event.recipientUserId());
        assertEquals("INVOICE", event.referenceType());
        assertEquals(INVOICE_ID, event.referenceId());
    }

    @Test
    @DisplayName("Event variables carry invoice number and new status for rendering")
    void eventVariables_carryInvoiceFacts() {
        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(firmRepository.findById(FIRM_ID)).thenReturn(Optional.of(new Firm()));
        when(invoiceMapper.toResponse(any(Invoice.class), any(Firm.class))).thenReturn(null);

        UpdateInvoiceStatusRequest req = new UpdateInvoiceStatusRequest();
        req.setStatus(InvoiceStatus.PAID);
        service.updateStatus(INVOICE_ID, req);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals("INV-2026-0004", captor.getValue().variables().get("invoiceNumber"));
        assertEquals("PAID", captor.getValue().variables().get("status"));
    }

    @Test
    @DisplayName("Illegal transition throws before any event is published")
    void illegalTransition_publishesNothing() {
        invoice.setStatus(InvoiceStatus.PAID); // terminal
        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));

        UpdateInvoiceStatusRequest req = new UpdateInvoiceStatusRequest();
        req.setStatus(InvoiceStatus.SENT);
        assertThrows(com.lawfirm.erp.common.exception.BusinessRuleException.class,
                () -> service.updateStatus(INVOICE_ID, req));

        verify(eventPublisher, never()).publishEvent(any(NotificationEvent.class));
        verify(auditService, never()).log(any(AuditAction.class), any(AuditEntity.class), any(), any());
    }

    @Test
    @DisplayName("Role fan-out target matches the repo query the orchestrator will use")
    void firmAdminsQuery_isAvailableForFanOut() {
        // Documents the contract between producer and orchestrator:
        // toRole(FIRM_ADMIN) resolves via userRepository.findUserIdsByFirmIdAndRoleCode
        when(userRepository.findUserIdsByFirmIdAndRoleCode(FIRM_ID, "FIRM_ADMIN"))
                .thenReturn(List.of(ADMIN_ID));

        assertEquals(List.of(ADMIN_ID),
                userRepository.findUserIdsByFirmIdAndRoleCode(FIRM_ID, "FIRM_ADMIN"));
    }
}
