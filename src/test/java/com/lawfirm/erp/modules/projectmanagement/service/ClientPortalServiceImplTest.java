package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.modules.projectmanagement.dto.response.ClientProjectResponse;
import com.lawfirm.erp.modules.projectmanagement.entity.Project;
import com.lawfirm.erp.modules.projectmanagement.entity.Renewal;
import com.lawfirm.erp.modules.projectmanagement.entity.RenewalInstance;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectStatus;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalInstanceStatus;
import com.lawfirm.erp.modules.projectmanagement.mapper.ProjectMapper;
import com.lawfirm.erp.modules.projectmanagement.repository.ProjectRepository;
import com.lawfirm.erp.modules.projectmanagement.repository.RenewalInstanceRepository;
import com.lawfirm.erp.modules.projectmanagement.repository.RenewalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientPortalServiceImplTest {

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID PROJECT_1 = UUID.randomUUID();
    private static final UUID PROJECT_2 = UUID.randomUUID();

    @Mock private ProjectRepository projectRepository;
    @Mock private RenewalRepository renewalRepository;
    @Mock private RenewalInstanceRepository instanceRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private ProjectMapper projectMapper;
    @Mock private UserRepository userRepository;

    @InjectMocks private ClientPortalServiceImpl clientPortalService;

    private Project project(UUID id, String code) {
        Project project = Project.builder()
                .firmId(UUID.randomUUID())
                .projectCode(code)
                .name(code)
                .clientName("Client")
                .status(ProjectStatus.ACTIVE)
                .ownerId(UUID.randomUUID())
                .build();
        project.setId(id);
        return project;
    }

    private void stubPortalClient() {
        when(currentUserResolver.getCurrentUserId()).thenReturn(CLIENT_ID);
        when(userRepository.findById(CLIENT_ID)).thenReturn(Optional.of(User.builder()
                .userType(UserType.CLIENT)
                .portalAccessEnabled(true)
                .build()));
    }

    @Test
    @DisplayName("Projects are listed with one renewals query and one instances query, not per project")
    void listMyProjects_batchesInsteadOfNPlusOne() {
        stubPortalClient();
        when(projectRepository.findByClientUserIdAndActive(CLIENT_ID, true))
                .thenReturn(List.of(project(PROJECT_1, "PRJ-1"), project(PROJECT_2, "PRJ-2")));
        when(renewalRepository.findByProjectIdInAndActive(anyList(), eq(true)))
                .thenReturn(List.of(
                        Renewal.builder().id(1L).projectId(PROJECT_1).renewalTypeId(10L).title("Tax").active(true).build(),
                        Renewal.builder().id(2L).projectId(PROJECT_2).renewalTypeId(20L).title("Licence").active(true).build()));
        when(instanceRepository.findByRenewalIdInAndActive(anyList(), eq(true)))
                .thenReturn(List.of(
                        RenewalInstance.builder().id(11L).renewalId(1L)
                                .dueDate(LocalDate.now().plusDays(5))
                                .status(RenewalInstanceStatus.PENDING).active(true).build(),
                        RenewalInstance.builder().id(21L).renewalId(2L)
                                .dueDate(LocalDate.now().plusYears(2))
                                .status(RenewalInstanceStatus.PENDING).active(true).build()));
        when(projectMapper.toClientProjectResponse(any(), anyList()))
                .thenReturn(ClientProjectResponse.builder().build());

        List<ClientProjectResponse> result = clientPortalService.listMyProjects();

        assertEquals(2, result.size());

        // The batched path runs once; the per-project / per-renewal lookups are gone.
        verify(renewalRepository, times(1)).findByProjectIdInAndActive(anyList(), eq(true));
        verify(instanceRepository, times(1)).findByRenewalIdInAndActive(anyList(), eq(true));
        verify(renewalRepository, never()).findByProjectIdAndActive(any(), anyBoolean());
        verify(instanceRepository, never()).findByRenewalIdAndActive(anyLong(), anyBoolean());

        // Only the near-term instance is within the 6-month window, and it belongs to project 1.
        ArgumentCaptor<List<RenewalInstance>> captor = ArgumentCaptor.forClass(List.class);
        verify(projectMapper, times(2)).toClientProjectResponse(any(), captor.capture());
        assertEquals(1, captor.getAllValues().get(0).size());
        assertEquals(0, captor.getAllValues().get(1).size());
    }

    @Test
    @DisplayName("A client with no projects issues no renewal/instance queries")
    void listMyProjects_noProjects() {
        stubPortalClient();
        when(projectRepository.findByClientUserIdAndActive(CLIENT_ID, true)).thenReturn(List.of());

        List<ClientProjectResponse> result = clientPortalService.listMyProjects();

        assertTrue(result.isEmpty());
        verifyNoInteractions(renewalRepository);
        verifyNoInteractions(instanceRepository);
        verifyNoInteractions(projectMapper);
    }
}
