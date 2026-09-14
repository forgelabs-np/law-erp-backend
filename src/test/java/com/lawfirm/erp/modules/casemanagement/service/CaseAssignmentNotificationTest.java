package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.casemanagement.dto.request.AssignCaseRequest;
import com.lawfirm.erp.modules.casemanagement.enums.AssignmentRole;
import com.lawfirm.erp.modules.casemanagement.repository.CaseAssignmentRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import com.lawfirm.erp.modules.notification.event.NotificationEvent;
import com.lawfirm.erp.modules.notification.enums.NotificationType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Producer wiring: assign() must publish exactly one CASE_ASSIGNED event.
 */
@ExtendWith(MockitoExtension.class)
class CaseAssignmentNotificationTest {

    @Mock private CaseAssignmentRepository assignmentRepository;
    @Mock private MatterRepository matterRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private CaseAssignmentServiceImpl service;

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID MATTER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String MATTER_NUMBER = "APX-MAT-2026-00042";

    private User assignee;

    @BeforeEach
    void setUp() {
        FirmContextHolder.set(FIRM_ID, "APX");
        Firm firm = new Firm();
        firm.setId(FIRM_ID);

        assignee = new User();
        assignee.setId(USER_ID);
        assignee.setFullName("Ram Shrestha");
        assignee.setFirm(firm);
        assignee.setUserType(UserType.FIRM_USER);
    }

    @AfterEach
    void tearDown() {
        FirmContextHolder.clear();
    }

    private AssignCaseRequest request() {
        AssignCaseRequest req = new AssignCaseRequest();
        req.setUserId(USER_ID);
        req.setAssignmentRole(AssignmentRole.PRIMARY_ADVOCATE);
        return req;
    }

    @Test
    @DisplayName("assign publishes CASE_ASSIGNED for the assignee with matter reference")
    @SuppressWarnings("unchecked")
    void assign_publishesCaseAssignedEvent() {
        com.lawfirm.erp.modules.casemanagement.entity.Matter matter =
                new com.lawfirm.erp.modules.casemanagement.entity.Matter();
        matter.setId(MATTER_ID);
        matter.setFirmId(FIRM_ID);
        matter.setMatterNumber(MATTER_NUMBER);

        when(matterRepository.findByMatterNumberAndFirmId(MATTER_NUMBER, FIRM_ID))
                .thenReturn(Optional.of(matter));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(assignee));
        when(assignmentRepository.existsByMatterIdAndUserIdAndFirmId(MATTER_ID, USER_ID, FIRM_ID))
                .thenReturn(false);
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.assign(MATTER_NUMBER, request());

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        NotificationEvent event = captor.getValue();
        assertEquals(NotificationType.CASE_ASSIGNED, event.type());
        assertEquals(FIRM_ID, event.firmId());
        assertEquals(USER_ID, event.recipientUserId());
        assertEquals("MATTER", event.referenceType());
        assertEquals(MATTER_ID, event.referenceId());
    }

    @Test
    @DisplayName("Duplicate-assign rejection publishes no event")
    void assignDuplicate_publishesNothing() {
        com.lawfirm.erp.modules.casemanagement.entity.Matter matter =
                new com.lawfirm.erp.modules.casemanagement.entity.Matter();
        matter.setId(MATTER_ID);
        matter.setFirmId(FIRM_ID);
        matter.setMatterNumber(MATTER_NUMBER);

        when(matterRepository.findByMatterNumberAndFirmId(MATTER_NUMBER, FIRM_ID))
                .thenReturn(Optional.of(matter));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(assignee));
        when(assignmentRepository.existsByMatterIdAndUserIdAndFirmId(MATTER_ID, USER_ID, FIRM_ID))
                .thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.assign(MATTER_NUMBER, request()));
        verify(eventPublisher, never()).publishEvent(any(NotificationEvent.class));
    }
}
