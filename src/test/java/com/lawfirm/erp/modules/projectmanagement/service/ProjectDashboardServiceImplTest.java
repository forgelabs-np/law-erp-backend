package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.modules.projectmanagement.dto.response.ProjectDashboardResponse;
import com.lawfirm.erp.modules.projectmanagement.entity.Project;
import com.lawfirm.erp.modules.projectmanagement.entity.Renewal;
import com.lawfirm.erp.modules.projectmanagement.entity.RenewalInstance;
import com.lawfirm.erp.modules.projectmanagement.entity.RenewalType;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectStatus;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalInstanceStatus;
import com.lawfirm.erp.modules.projectmanagement.repository.CredentialRepository;
import com.lawfirm.erp.modules.projectmanagement.repository.ProjectRepository;
import com.lawfirm.erp.modules.projectmanagement.repository.RenewalInstanceRepository;
import com.lawfirm.erp.modules.projectmanagement.repository.RenewalRepository;
import com.lawfirm.erp.modules.projectmanagement.repository.RenewalTypeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectDashboardServiceImplTest {

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID PROJECT_1 = UUID.randomUUID();
    private static final UUID PROJECT_2 = UUID.randomUUID();

    @Mock private ProjectRepository projectRepository;
    @Mock private CredentialRepository credentialRepository;
    @Mock private RenewalRepository renewalRepository;
    @Mock private RenewalInstanceRepository instanceRepository;
    @Mock private RenewalTypeRepository renewalTypeRepository;

    @InjectMocks private ProjectDashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        FirmContextHolder.set(FIRM_ID, "TST");
    }

    @AfterEach
    void tearDown() {
        FirmContextHolder.clear();
    }

    private Project project(UUID id, String code, String name) {
        Project project = Project.builder()
                .firmId(FIRM_ID)
                .projectCode(code)
                .name(name)
                .clientName("Client")
                .status(ProjectStatus.ACTIVE)
                .ownerId(UUID.randomUUID())
                .build();
        project.setId(id);
        return project;
    }

    private Renewal renewal(long id, UUID projectId, long typeId, String title) {
        return Renewal.builder()
                .id(id)
                .projectId(projectId)
                .renewalTypeId(typeId)
                .title(title)
                .active(true)
                .build();
    }

    private RenewalInstance instance(long id, long renewalId, LocalDate dueDate, RenewalInstanceStatus status) {
        return RenewalInstance.builder()
                .id(id)
                .renewalId(renewalId)
                .dueDate(dueDate)
                .status(status)
                .active(true)
                .build();
    }

    private void stubHappyPath() {
        stubHappyPath(List.of(
                RenewalType.builder().id(10L).name("Tax").build(),
                RenewalType.builder().id(20L).name("Licence").build()));
    }

    private void stubHappyPath(List<RenewalType> renewalTypes) {
        when(projectRepository.findByFirmId(eq(FIRM_ID), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(
                        project(PROJECT_1, "PRJ-1", "First"),
                        project(PROJECT_2, "PRJ-2", "Second"))));
        when(projectRepository.countByFirmIdAndStatus(FIRM_ID, ProjectStatus.ACTIVE)).thenReturn(2L);
        when(projectRepository.countByFirmIdAndStatus(FIRM_ID, ProjectStatus.ON_HOLD)).thenReturn(0L);
        when(projectRepository.countByFirmIdAndStatus(FIRM_ID, ProjectStatus.COMPLETED)).thenReturn(0L);
        when(credentialRepository.countByProjectIdInAndActive(anyList(), eq(true))).thenReturn(5L);
        when(renewalRepository.countByProjectIdInAndActive(anyList(), eq(true))).thenReturn(3L);
        when(renewalRepository.findByProjectIdInAndActive(anyList(), eq(true)))
                .thenReturn(List.of(
                        renewal(1L, PROJECT_1, 10L, "Tax filing"),
                        renewal(2L, PROJECT_2, 20L, "Licence")));
        when(instanceRepository.findByRenewalIdInAndActive(anyList(), eq(true)))
                .thenReturn(List.of(
                        instance(11L, 1L, LocalDate.now().minusDays(4), RenewalInstanceStatus.OVERDUE),
                        instance(21L, 2L, LocalDate.now().plusDays(10), RenewalInstanceStatus.PENDING),
                        instance(22L, 2L, LocalDate.now().plusYears(2), RenewalInstanceStatus.PENDING)));
        when(renewalTypeRepository.findAllById(anyList())).thenReturn(renewalTypes);
    }

    @Test
    @DisplayName("Dashboard runs a fixed number of queries, not one per project/renewal")
    void getDashboard_batchesInsteadOfNPlusOne() {
        stubHappyPath();

        dashboardService.getDashboard();

        verify(renewalRepository, times(1)).findByProjectIdInAndActive(anyList(), eq(true));
        verify(instanceRepository, times(1)).findByRenewalIdInAndActive(anyList(), eq(true));
        verify(renewalTypeRepository, times(1)).findAllById(anyList());
        verify(renewalRepository, times(1)).countByProjectIdInAndActive(anyList(), eq(true));
        verify(credentialRepository, times(1)).countByProjectIdInAndActive(anyList(), eq(true));

        // The per-project / per-renewal lookups must be gone.
        verify(renewalRepository, never()).findByProjectIdAndActive(any(), anyBoolean());
        verify(instanceRepository, never()).findByRenewalIdAndActive(anyLong(), anyBoolean());
        verify(renewalTypeRepository, never()).findById(anyLong());
        verify(renewalRepository, never()).countByProjectIdAndActive(any(), anyBoolean());
        verify(credentialRepository, never()).countByProjectIdAndActive(any(), anyBoolean());
    }

    @Test
    @DisplayName("Totals and the overdue/upcoming buckets are unchanged by the batching")
    void getDashboard_keepsTheSameResult() {
        stubHappyPath();

        ProjectDashboardResponse response = dashboardService.getDashboard();

        assertEquals(2L, response.getTotalProjects());
        assertEquals(2L, response.getActiveProjects());
        assertEquals(5L, response.getTotalCredentials());
        assertEquals(3L, response.getTotalRenewals());

        assertEquals(1, response.getOverdueItems().size());
        ProjectDashboardResponse.OverdueItem overdue = response.getOverdueItems().get(0);
        assertEquals("PRJ-1", overdue.getProjectCode());
        assertEquals("First", overdue.getProjectName());
        assertEquals("Tax", overdue.getRenewalTypeName());
        assertEquals(4, overdue.getDaysOverdue());

        // The PENDING instance two years out is outside the 3-month window.
        assertEquals(1, response.getUpcomingItems().size());
        ProjectDashboardResponse.UpcomingItem upcoming = response.getUpcomingItems().get(0);
        assertEquals("PRJ-2", upcoming.getProjectCode());
        assertEquals("Licence", upcoming.getRenewalTypeName());
        assertEquals(10, upcoming.getDaysUntilDue());
    }

    @Test
    @DisplayName("An unknown renewal type degrades to \"Unknown\" rather than failing")
    void getDashboard_unknownRenewalType() {
        // Only the Tax type exists; the Licence renewal must degrade, not fail.
        stubHappyPath(List.of(RenewalType.builder().id(10L).name("Tax").build()));

        ProjectDashboardResponse response = dashboardService.getDashboard();

        assertEquals("Unknown", response.getUpcomingItems().get(0).getRenewalTypeName());
    }

    @Test
    @DisplayName("No projects skips the IN(...) queries entirely")
    void getDashboard_noProjects() {
        when(projectRepository.findByFirmId(eq(FIRM_ID), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(projectRepository.countByFirmIdAndStatus(eq(FIRM_ID), any())).thenReturn(0L);

        ProjectDashboardResponse response = dashboardService.getDashboard();

        assertEquals(0L, response.getTotalProjects());
        assertTrue(response.getOverdueItems().isEmpty());
        verifyNoInteractions(renewalRepository);
        verifyNoInteractions(instanceRepository);
        verifyNoInteractions(credentialRepository);
        verifyNoInteractions(renewalTypeRepository);
    }
}
