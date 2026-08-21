package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.projectmanagement.dto.request.AddMemberRequest;
import com.lawfirm.erp.modules.projectmanagement.dto.request.CreateProjectRequest;
import com.lawfirm.erp.modules.projectmanagement.dto.response.ProjectResponse;
import com.lawfirm.erp.modules.projectmanagement.entity.Project;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectMemberRole;
import com.lawfirm.erp.modules.projectmanagement.enums.ProjectStatus;
import com.lawfirm.erp.modules.projectmanagement.mapper.ProjectMapper;
import com.lawfirm.erp.modules.projectmanagement.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectServiceTest {

    private static final UUID FIRM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private ProjectRepository projectRepository;
    @Mock private CredentialRepository credentialRepository;
    @Mock private RenewalRepository renewalRepository;
    @Mock private RenewalInstanceRepository instanceRepository;
    @Mock private ProjectMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private ProjectMapper projectMapper;
    @Mock private CurrentUserResolver currentUserResolver;

    @InjectMocks
    private ProjectServiceImpl projectService;

    @BeforeEach
    void setUp() {
        FirmContextHolder.set(FIRM_ID, "APX");
        when(currentUserResolver.getCurrentUserId()).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        FirmContextHolder.clear();
    }

    private User mockUser(UUID id) {
        User user = new User();
        user.setId(id);
        Firm firm = new Firm();
        firm.setId(FIRM_ID);
        user.setFirm(firm);
        user.setFullName("Test User");
        user.setUsername("testuser");
        return user;
    }

    @Test
    @DisplayName("Create project generates code and adds owner as member")
    void createProjectHappyPath() {
        User owner = mockUser(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(owner));
        when(projectRepository.findByFirmId(any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(projectMapper.toProjectResponse(any(), any(), anyInt(), anyInt(), anyLong()))
                .thenReturn(ProjectResponse.builder().build());

        CreateProjectRequest request = new CreateProjectRequest();
        request.setName("Test Project");
        request.setClientName("Test Client");

        ProjectResponse response = projectService.createProject(request);

        assertNotNull(response);
        verify(memberRepository).save(any());
    }

    @Test
    @DisplayName("Create project with non-existent client throws")
    void createProjectNonExistentClient() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser(USER_ID)));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        CreateProjectRequest request = new CreateProjectRequest();
        request.setName("Test");
        request.setClientName("Client");
        request.setClientUserId(UUID.randomUUID());

        assertThrows(ResourceNotFoundException.class,
                () -> projectService.createProject(request));
    }

    @Test
    @DisplayName("Get project with invalid code throws ResourceNotFoundException")
    void getProjectNotFound() {
        when(projectRepository.findByProjectCodeAndFirmId("INVALID", FIRM_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> projectService.getProject("INVALID"));
    }

    @Test
    @DisplayName("Add member to project succeeds")
    void addMemberHappyPath() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setFirmId(FIRM_ID);

        when(projectRepository.findByProjectCodeAndFirmId("APX-PRJ-2026-00001", FIRM_ID))
                .thenReturn(Optional.of(project));
        when(userRepository.findById(any())).thenReturn(Optional.of(mockUser(UUID.randomUUID())));
        when(memberRepository.existsByProjectIdAndUserId(any(), any())).thenReturn(false);
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(projectMapper.toMemberResponse(any())).thenReturn(
                com.lawfirm.erp.modules.projectmanagement.dto.response.ProjectMemberResponse.builder().build());

        AddMemberRequest request = new AddMemberRequest();
        request.setUserId(UUID.randomUUID());
        request.setRole(ProjectMemberRole.MEMBER);

        var response = projectService.addMember("APX-PRJ-2026-00001", request);
        assertNotNull(response);
        verify(memberRepository).save(any());
    }

    @Test
    @DisplayName("Remove owner from project throws BusinessRuleException")
    void removeOwnerThrows() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setFirmId(FIRM_ID);

        UUID ownerId = UUID.randomUUID();
        when(projectRepository.findByProjectCodeAndFirmId("APX-PRJ-2026-00001", FIRM_ID))
                .thenReturn(Optional.of(project));

        com.lawfirm.erp.modules.projectmanagement.entity.ProjectMember ownerMember =
                com.lawfirm.erp.modules.projectmanagement.entity.ProjectMember.builder()
                        .userId(ownerId)
                        .roleInProject(ProjectMemberRole.OWNER)
                        .build();

        when(memberRepository.findByProjectIdAndUserId(project.getId(), ownerId))
                .thenReturn(Optional.of(ownerMember));

        assertThrows(com.lawfirm.erp.common.exception.BusinessRuleException.class,
                () -> projectService.removeMember("APX-PRJ-2026-00001", ownerId));
    }
}
