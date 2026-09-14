package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.constant.ProjectManagementConstants;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.util.ConfigEncryptionUtil;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.modules.projectmanagement.dto.request.*;
import com.lawfirm.erp.modules.projectmanagement.dto.response.CredentialResponse;
import com.lawfirm.erp.modules.projectmanagement.entity.Credential;
import com.lawfirm.erp.modules.projectmanagement.entity.Project;
import com.lawfirm.erp.modules.projectmanagement.mapper.ProjectMapper;
import com.lawfirm.erp.modules.projectmanagement.repository.CredentialRepository;
import com.lawfirm.erp.modules.projectmanagement.repository.ProjectRepository;
import com.lawfirm.erp.auth.security.FirmContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialServiceImpl implements CredentialService {

    private final CredentialRepository credentialRepository;
    private final ProjectRepository projectRepository;
    private final ConfigEncryptionUtil encryptionUtil;
    private final AuditService auditService;
    private final CurrentUserResolver currentUserResolver;
    private final ProjectMapper projectMapper;

    @Transactional
    public CredentialResponse addCredential(String projectCode, AddCredentialRequest request) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);

        Credential credential = Credential.builder()
                .projectId(project.getId())
                .siteName(request.getSiteName())
                .siteType(request.getSiteType())
                .siteUrl(request.getSiteUrl())
                .usernameOrEmail(request.getUsernameOrEmail())
                .encryptedPassword(encryptionUtil.encrypt(request.getPassword()))
                .contactPerson(request.getContactPerson())
                .contactPhone(request.getContactPhone())
                .contactEmail(request.getContactEmail())
                .notes(request.getNotes())
                .active(true)
                .build();
        credential = credentialRepository.save(credential);

        auditService.log(AuditAction.PROJECT_UPDATED, AuditEntity.PROJECT, project.getId(),
                "Credential added: " + request.getSiteName() + " to project " + projectCode);

        return projectMapper.toCredentialResponse(credential);
    }

    public List<CredentialResponse> listCredentials(String projectCode) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);
        return credentialRepository.findByProjectIdAndActive(project.getId(), true).stream()
                .map(projectMapper::toCredentialResponse)
                .collect(Collectors.toList());
    }

    public CredentialResponse getCredential(String projectCode, Long credentialId) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);
        Credential credential = findCredential(credentialId, project.getId());
        return projectMapper.toCredentialResponse(credential);
    }

    @Transactional
    public CredentialResponse updateCredential(String projectCode, Long credentialId,
                                                UpdateCredentialRequest request) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);
        Credential credential = findCredential(credentialId, project.getId());

        if (request.getSiteName() != null) credential.setSiteName(request.getSiteName());
        if (request.getSiteType() != null) credential.setSiteType(request.getSiteType());
        if (request.getSiteUrl() != null) credential.setSiteUrl(request.getSiteUrl());
        if (request.getUsernameOrEmail() != null) credential.setUsernameOrEmail(request.getUsernameOrEmail());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            credential.setEncryptedPassword(encryptionUtil.encrypt(request.getPassword()));
        }
        if (request.getContactPerson() != null) credential.setContactPerson(request.getContactPerson());
        if (request.getContactPhone() != null) credential.setContactPhone(request.getContactPhone());
        if (request.getContactEmail() != null) credential.setContactEmail(request.getContactEmail());
        if (request.getNotes() != null) credential.setNotes(request.getNotes());

        credential = credentialRepository.save(credential);
        return projectMapper.toCredentialResponse(credential);
    }

    @Transactional
    public void deleteCredential(String projectCode, Long credentialId) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);
        Credential credential = findCredential(credentialId, project.getId());
        credential.setActive(false);
        credentialRepository.save(credential);
    }

    @Transactional
    public String revealPassword(String projectCode, Long credentialId) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);
        Credential credential = findCredential(credentialId, project.getId());

        UUID currentUserId = currentUserResolver.getCurrentUserId();
        log.warn("Password reveal: user={} credential={} site={} project={}",
                currentUserId, credentialId, credential.getSiteName(), projectCode);

        auditService.log(AuditAction.PROJECT_UPDATED, AuditEntity.PROJECT, project.getId(),
                "Password revealed for credential: " + credential.getSiteName()
                        + " by user: " + currentUserId);

        return encryptionUtil.decrypt(credential.getEncryptedPassword());
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private Project findProject(String projectCode, UUID firmId) {
        return projectRepository.findByProjectCodeAndFirmId(projectCode, firmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ProjectManagementConstants.PROJECT_NOT_FOUND));
    }

    private Credential findCredential(Long credentialId, UUID projectId) {
        return credentialRepository.findById(credentialId)
                .filter(c -> c.getProjectId().equals(projectId) && c.isActive())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ProjectManagementConstants.CREDENTIAL_NOT_FOUND));
    }

    private UUID getRequiredFirmId() {
        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }
}
