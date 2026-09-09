package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.casemanagement.dto.request.AssignCaseRequest;
import com.lawfirm.erp.modules.casemanagement.dto.response.CaseAssignmentResponse;
import com.lawfirm.erp.modules.casemanagement.entity.CaseAssignment;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.enums.AssignmentRole;
import com.lawfirm.erp.modules.casemanagement.enums.MatterStatus;
import com.lawfirm.erp.modules.casemanagement.enums.MatterType;
import com.lawfirm.erp.modules.casemanagement.enums.CourtLevel;
import com.lawfirm.erp.modules.casemanagement.repository.CaseAssignmentRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaseAssignmentServiceTest {

    @Mock private CaseAssignmentRepository assignmentRepository;
    @Mock private MatterRepository matterRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private CaseAssignmentServiceImpl service;

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID MATTER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String MATTER_NUMBER = "APX-MAT-2026-00001";

    private Matter matter;
    private User user;

    @BeforeEach
    void setUp() {
        FirmContextHolder.set(FIRM_ID, "APX");

        Firm firm = new Firm();
        firm.setId(FIRM_ID);

        matter = new Matter();
        matter.setId(MATTER_ID);
        matter.setFirmId(FIRM_ID);
        matter.setMatterNumber(MATTER_NUMBER);
        matter.setTitle("Test Matter");
        matter.setMatterType(MatterType.CIVIL);
        matter.setStatus(MatterStatus.ACTIVE);
        matter.setOriginatingCourtLevel(CourtLevel.DISTRICT);

        user = new User();
        user.setId(USER_ID);
        user.setFullName("Ram Shrestha");
        user.setFirm(firm);
        user.setUserType(UserType.FIRM_USER);
    }

    @Test
    @DisplayName("Assign employee to matter creates assignment")
    void assign_createsAssignment() {
        when(matterRepository.findByMatterNumberAndFirmId(MATTER_NUMBER, FIRM_ID))
                .thenReturn(Optional.of(matter));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(assignmentRepository.existsByMatterIdAndUserIdAndFirmId(MATTER_ID, USER_ID, FIRM_ID))
                .thenReturn(false);
        when(assignmentRepository.save(any(CaseAssignment.class))).thenAnswer(inv -> {
            CaseAssignment a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        AssignCaseRequest req = new AssignCaseRequest();
        req.setUserId(USER_ID);
        req.setAssignmentRole(AssignmentRole.PRIMARY_ADVOCATE);

        CaseAssignmentResponse resp = service.assign(MATTER_NUMBER, req);

        assertNotNull(resp);
        assertEquals(MATTER_ID, resp.getMatterId());
        assertEquals(USER_ID, resp.getUserId());
        assertEquals(AssignmentRole.PRIMARY_ADVOCATE, resp.getAssignmentRole());
        assertEquals("Ram Shrestha", resp.getUserName());
        verify(assignmentRepository).save(any());
        verify(auditService).log(any(), any(), eq(MATTER_ID), anyString());
    }

    @Test
    @DisplayName("Assign duplicate user throws BusinessRuleException")
    void assign_duplicate_throws() {
        when(matterRepository.findByMatterNumberAndFirmId(MATTER_NUMBER, FIRM_ID))
                .thenReturn(Optional.of(matter));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(assignmentRepository.existsByMatterIdAndUserIdAndFirmId(MATTER_ID, USER_ID, FIRM_ID))
                .thenReturn(true);

        AssignCaseRequest req = new AssignCaseRequest();
        req.setUserId(USER_ID);
        req.setAssignmentRole(AssignmentRole.PARALEGAL);

        assertThrows(BusinessRuleException.class, () -> service.assign(MATTER_NUMBER, req));
    }

    @Test
    @DisplayName("Assign user from different firm throws BusinessRuleException")
    void assign_wrongFirm_throws() {
        Firm otherFirm = new Firm();
        otherFirm.setId(UUID.randomUUID());
        User otherUser = new User();
        otherUser.setId(USER_ID);
        otherUser.setFirm(otherFirm);

        when(matterRepository.findByMatterNumberAndFirmId(MATTER_NUMBER, FIRM_ID))
                .thenReturn(Optional.of(matter));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(otherUser));

        AssignCaseRequest req = new AssignCaseRequest();
        req.setUserId(USER_ID);
        req.setAssignmentRole(AssignmentRole.JUNIOR);

        assertThrows(BusinessRuleException.class, () -> service.assign(MATTER_NUMBER, req));
    }

    @Test
    @DisplayName("Assign a CLIENT user throws BusinessRuleException")
    void assign_client_throws() {
        User client = new User();
        client.setId(USER_ID);
        client.setFirm(user.getFirm());
        client.setUserType(UserType.CLIENT);

        when(matterRepository.findByMatterNumberAndFirmId(MATTER_NUMBER, FIRM_ID))
                .thenReturn(Optional.of(matter));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(client));

        AssignCaseRequest req = new AssignCaseRequest();
        req.setUserId(USER_ID);
        req.setAssignmentRole(AssignmentRole.PARALEGAL);

        assertThrows(BusinessRuleException.class, () -> service.assign(MATTER_NUMBER, req));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Revoke assignment deletes records")
    void revoke_deletesAssignments() {
        when(matterRepository.findByMatterNumberAndFirmId(MATTER_NUMBER, FIRM_ID))
                .thenReturn(Optional.of(matter));

        CaseAssignment a = new CaseAssignment();
        a.setId(UUID.randomUUID());
        a.setMatterId(MATTER_ID);
        a.setUserId(USER_ID);
        a.setFirmId(FIRM_ID);

        when(assignmentRepository.findByMatterIdAndUserIdAndFirmId(MATTER_ID, USER_ID, FIRM_ID))
                .thenReturn(List.of(a));

        service.revoke(MATTER_NUMBER, USER_ID);

        verify(assignmentRepository).deleteAll(List.of(a));
        verify(auditService).log(any(), any(), eq(MATTER_ID), anyString());
    }

    @Test
    @DisplayName("Revoke non-existent assignment throws")
    void revoke_notFound_throws() {
        when(matterRepository.findByMatterNumberAndFirmId(MATTER_NUMBER, FIRM_ID))
                .thenReturn(Optional.of(matter));
        when(assignmentRepository.findByMatterIdAndUserIdAndFirmId(MATTER_ID, USER_ID, FIRM_ID))
                .thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> service.revoke(MATTER_NUMBER, USER_ID));
    }

    @Test
    @DisplayName("List assignments by matter returns responses")
    void listByMatter_returnsList() {
        when(matterRepository.findByMatterNumberAndFirmId(MATTER_NUMBER, FIRM_ID))
                .thenReturn(Optional.of(matter));

        CaseAssignment a = new CaseAssignment();
        a.setId(UUID.randomUUID());
        a.setMatterId(MATTER_ID);
        a.setUserId(USER_ID);
        a.setFirmId(FIRM_ID);
        a.setAssignmentRole(AssignmentRole.CO_ADVOCATE);

        when(assignmentRepository.findByMatterIdAndFirmId(MATTER_ID, FIRM_ID))
                .thenReturn(List.of(a));
        when(userRepository.findAllById(List.of(USER_ID))).thenReturn(List.of(user));

        List<CaseAssignmentResponse> result = service.listByMatter(MATTER_NUMBER);

        assertEquals(1, result.size());
        assertEquals(AssignmentRole.CO_ADVOCATE, result.get(0).getAssignmentRole());
    }

    @Test
    @DisplayName("isAssigned returns true when user is assigned")
    void isAssigned_true() {
        when(assignmentRepository.existsByMatterIdAndUserIdAndFirmId(MATTER_ID, USER_ID, FIRM_ID))
                .thenReturn(true);

        assertTrue(service.isAssigned(MATTER_ID, USER_ID, FIRM_ID));
    }

    @Test
    @DisplayName("getAssignedMatterIds returns list")
    void getAssignedMatterIds_returnsList() {
        when(assignmentRepository.findMatterIdsByUserIdAndFirmId(USER_ID, FIRM_ID))
                .thenReturn(List.of(MATTER_ID));

        List<UUID> result = service.getAssignedMatterIds(USER_ID, FIRM_ID);

        assertEquals(1, result.size());
        assertEquals(MATTER_ID, result.get(0));
    }
}
