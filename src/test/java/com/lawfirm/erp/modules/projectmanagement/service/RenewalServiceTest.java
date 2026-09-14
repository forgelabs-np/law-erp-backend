package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.projectmanagement.dto.request.CreateRenewalRequest;
import com.lawfirm.erp.modules.projectmanagement.dto.response.RenewalResponse;
import com.lawfirm.erp.modules.projectmanagement.entity.Project;
import com.lawfirm.erp.modules.projectmanagement.entity.Renewal;
import com.lawfirm.erp.modules.projectmanagement.entity.RenewalType;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalInstanceStatus;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalRecurrence;
import com.lawfirm.erp.modules.projectmanagement.mapper.ProjectMapper;
import com.lawfirm.erp.modules.projectmanagement.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RenewalServiceTest {

    private static final UUID FIRM_ID = UUID.randomUUID();

    @Mock private RenewalRepository renewalRepository;
    @Mock private RenewalInstanceRepository instanceRepository;
    @Mock private RenewalTypeRepository renewalTypeRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private AuditService auditService;
    @Mock private ProjectMapper projectMapper;
    @Mock private CurrentUserResolver currentUserResolver;

    @InjectMocks
    private RenewalServiceImpl renewalService;

    @BeforeEach
    void setUp() {
        FirmContextHolder.set(FIRM_ID, "APX");
        when(currentUserResolver.getCurrentUserId()).thenReturn(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        FirmContextHolder.clear();
    }

    private Project mockProject() {
        Project p = new Project();
        p.setId(UUID.randomUUID());
        p.setFirmId(FIRM_ID);
        p.setProjectCode("APX-PRJ-2026-00001");
        return p;
    }

    private RenewalType mockRenewalType() {
        RenewalType rt = new RenewalType();
        rt.setId(1L);
        rt.setName("Trademark Renewal");
        rt.setSystem(true);
        return rt;
    }

    @Test
    @DisplayName("Create ONE_TIME renewal generates exactly 1 instance")
    void createOneTimeRenewal() {
        Project project = mockProject();
        when(projectRepository.findByProjectCodeAndFirmId("APX-PRJ-2026-00001", FIRM_ID))
                .thenReturn(Optional.of(project));
        when(renewalTypeRepository.findById(1L)).thenReturn(Optional.of(mockRenewalType()));
        when(renewalRepository.save(any(Renewal.class))).thenAnswer(inv -> {
            Renewal r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(instanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(projectMapper.toRenewalResponse(any(), any(), any(), any()))
                .thenReturn(RenewalResponse.builder().build());

        CreateRenewalRequest request = new CreateRenewalRequest();
        request.setRenewalTypeId(1L);
        request.setTitle("Test Renewal");
        request.setRecurrence(RenewalRecurrence.ONE_TIME);
        request.setStartDate(LocalDate.of(2026, 9, 15));

        renewalService.createRenewal("APX-PRJ-2026-00001", request);

        ArgumentCaptor<com.lawfirm.erp.modules.projectmanagement.entity.RenewalInstance> captor =
                ArgumentCaptor.forClass(com.lawfirm.erp.modules.projectmanagement.entity.RenewalInstance.class);
        verify(instanceRepository, times(1)).save(captor.capture());

        assertEquals(LocalDate.of(2026, 9, 15), captor.getValue().getDueDate());
        assertEquals(RenewalInstanceStatus.PENDING, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Create YEARLY renewal generates 3 instances by default (no end date)")
    void createYearlyRenewalDefault() {
        Project project = mockProject();
        when(projectRepository.findByProjectCodeAndFirmId("APX-PRJ-2026-00001", FIRM_ID))
                .thenReturn(Optional.of(project));
        when(renewalTypeRepository.findById(1L)).thenReturn(Optional.of(mockRenewalType()));
        when(renewalRepository.save(any(Renewal.class))).thenAnswer(inv -> {
            Renewal r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(instanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(projectMapper.toRenewalResponse(any(), any(), any(), any()))
                .thenReturn(RenewalResponse.builder().build());

        CreateRenewalRequest request = new CreateRenewalRequest();
        request.setRenewalTypeId(1L);
        request.setTitle("Yearly Test");
        request.setRecurrence(RenewalRecurrence.YEARLY);
        request.setStartDate(LocalDate.of(2026, 1, 1));

        renewalService.createRenewal("APX-PRJ-2026-00001", request);

        // Default generates 3 years from NOW — from 2026-01-01 through ~2029
        verify(instanceRepository, atLeast(3)).save(any());
    }

    @Test
    @DisplayName("Create QUARTERLY renewal generates 4 instances per year")
    void createQuarterlyRenewal() {
        Project project = mockProject();
        when(projectRepository.findByProjectCodeAndFirmId("APX-PRJ-2026-00001", FIRM_ID))
                .thenReturn(Optional.of(project));
        when(renewalTypeRepository.findById(1L)).thenReturn(Optional.of(mockRenewalType()));
        when(renewalRepository.save(any(Renewal.class))).thenAnswer(inv -> {
            Renewal r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(instanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(projectMapper.toRenewalResponse(any(), any(), any(), any()))
                .thenReturn(RenewalResponse.builder().build());

        CreateRenewalRequest request = new CreateRenewalRequest();
        request.setRenewalTypeId(1L);
        request.setTitle("Quarterly Test");
        request.setRecurrence(RenewalRecurrence.QUARTERLY);
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 12, 31));

        renewalService.createRenewal("APX-PRJ-2026-00001", request);

        // 4 quarters in 2026
        verify(instanceRepository, times(4)).save(any());
    }

    @Test
    @DisplayName("Get renewal for non-existent project throws")
    void getRenewalProjectNotFound() {
        when(projectRepository.findByProjectCodeAndFirmId("INVALID", FIRM_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> renewalService.getRenewal("INVALID", 1L));
    }
}
