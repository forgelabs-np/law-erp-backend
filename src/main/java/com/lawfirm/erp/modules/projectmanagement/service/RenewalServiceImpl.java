package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.constant.ProjectManagementConstants;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.modules.projectmanagement.dto.request.*;
import com.lawfirm.erp.modules.projectmanagement.dto.response.*;
import com.lawfirm.erp.modules.projectmanagement.entity.*;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalInstanceStatus;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalRecurrence;
import com.lawfirm.erp.modules.projectmanagement.enums.RenewalStatus;
import com.lawfirm.erp.modules.projectmanagement.mapper.ProjectMapper;
import com.lawfirm.erp.modules.projectmanagement.repository.*;
import com.lawfirm.erp.auth.security.FirmContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RenewalServiceImpl implements RenewalService {

    private final RenewalRepository renewalRepository;
    private final RenewalInstanceRepository instanceRepository;
    private final RenewalTypeRepository renewalTypeRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;
    private final ProjectMapper projectMapper;

    private static final int DEFAULT_YEARS_AHEAD = 3;

    @Transactional
    public RenewalResponse createRenewal(String projectCode, CreateRenewalRequest request) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);

        // Validate renewal type exists
        RenewalType renewalType = renewalTypeRepository.findById(request.getRenewalTypeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ProjectManagementConstants.RENEWAL_TYPE_NOT_FOUND));

        Renewal renewal = Renewal.builder()
                .projectId(project.getId())
                .renewalTypeId(request.getRenewalTypeId())
                .title(request.getTitle())
                .description(request.getDescription())
                .recurrence(request.getRecurrence())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .assignedToId(request.getAssignedToId())
                .status(RenewalStatus.ACTIVE)
                .active(true)
                .build();
        renewal = renewalRepository.save(renewal);

        // Auto-generate instances for recurring renewals
        List<RenewalInstance> instances = generateInstances(renewal);

        auditService.log(AuditAction.PROJECT_UPDATED, AuditEntity.PROJECT, project.getId(),
                "Renewal created: " + request.getTitle() + " (" + request.getRecurrence() + ")");

        return projectMapper.toRenewalResponse(renewal, renewalType.getName(), null, instances);
    }

    public List<RenewalResponse> listRenewals(String projectCode) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);
        List<Renewal> renewals = renewalRepository.findByProjectIdAndActive(project.getId(), true);

        return renewals.stream()
                .map(r -> {
                    String typeName = renewalTypeRepository.findById(r.getRenewalTypeId())
                            .map(RenewalType::getName).orElse("Unknown");
                    List<RenewalInstance> instances = instanceRepository
                            .findByRenewalIdAndActive(r.getId(), true);
                    return projectMapper.toRenewalResponse(r, typeName, null, instances);
                })
                .collect(Collectors.toList());
    }

    public RenewalResponse getRenewal(String projectCode, Long renewalId) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);
        Renewal renewal = findRenewal(renewalId, project.getId());
        String typeName = renewalTypeRepository.findById(renewal.getRenewalTypeId())
                .map(RenewalType::getName).orElse("Unknown");
        List<RenewalInstance> instances = instanceRepository
                .findByRenewalIdAndActive(renewal.getId(), true);
        return projectMapper.toRenewalResponse(renewal, typeName, null, instances);
    }

    @Transactional
    public RenewalResponse updateRenewal(String projectCode, Long renewalId,
                                          UpdateRenewalRequest request) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);
        Renewal renewal = findRenewal(renewalId, project.getId());

        if (request.getRenewalTypeId() != null) renewal.setRenewalTypeId(request.getRenewalTypeId());
        if (request.getTitle() != null) renewal.setTitle(request.getTitle());
        if (request.getDescription() != null) renewal.setDescription(request.getDescription());
        if (request.getRecurrence() != null) renewal.setRecurrence(request.getRecurrence());
        if (request.getStartDate() != null) renewal.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) renewal.setEndDate(request.getEndDate());
        if (request.getAssignedToId() != null) renewal.setAssignedToId(request.getAssignedToId());

        renewal = renewalRepository.save(renewal);
        return getRenewal(projectCode, renewalId);
    }

    @Transactional
    public RenewalResponse updateRenewalStatus(String projectCode, Long renewalId,
                                                RenewalStatus status) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);
        Renewal renewal = findRenewal(renewalId, project.getId());
        renewal.setStatus(status);
        renewal = renewalRepository.save(renewal);
        return getRenewal(projectCode, renewalId);
    }

    @Transactional
    public RenewalInstanceResponse updateInstanceStatus(String projectCode, Long renewalId,
                                                        Long instanceId,
                                                        UpdateInstanceStatusRequest request) {
        UUID firmId = getRequiredFirmId();
        Project project = findProject(projectCode, firmId);
        Renewal renewal = findRenewal(renewalId, project.getId());

        RenewalInstance instance = instanceRepository.findById(instanceId)
                .filter(i -> i.getRenewalId().equals(renewal.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Renewal instance not found"));

        instance.setStatus(request.getStatus());
        if (request.getNotes() != null) instance.setNotes(request.getNotes());

        if (request.getStatus() == RenewalInstanceStatus.COMPLETED) {
            instance.setCompletedAt(java.time.LocalDateTime.now());
            instance.setCompletedById(currentUserResolver.getCurrentUserId());
        }

        instance = instanceRepository.save(instance);
        return projectMapper.toInstanceResponse(instance);
    }

    // ─── Recurrence Engine ─────────────────────────────────────────────────

    private List<RenewalInstance> generateInstances(Renewal renewal) {
        if (renewal.getRecurrence() == RenewalRecurrence.ONE_TIME) {
            return List.of(createInstance(renewal, renewal.getStartDate()));
        }

        List<RenewalInstance> instances = new ArrayList<>();
        LocalDate cursor = renewal.getStartDate();
        LocalDate effectiveEnd = renewal.getEndDate() != null
                ? renewal.getEndDate()
                : LocalDate.now().plusYears(DEFAULT_YEARS_AHEAD);

        while (!cursor.isAfter(effectiveEnd)) {
            instances.add(createInstance(renewal, cursor));
            cursor = nextOccurrence(cursor, renewal.getRecurrence());
        }
        return instances;
    }

    private RenewalInstance createInstance(Renewal renewal, LocalDate dueDate) {
        RenewalInstance instance = RenewalInstance.builder()
                .renewalId(renewal.getId())
                .dueDate(dueDate)
                .status(RenewalInstanceStatus.PENDING)
                .active(true)
                .build();
        return instanceRepository.save(instance);
    }

    private LocalDate nextOccurrence(LocalDate current, RenewalRecurrence recurrence) {
        return switch (recurrence) {
            case MONTHLY -> current.plusMonths(1);
            case QUARTERLY -> current.plusMonths(3);
            case YEARLY -> current.plusYears(1);
            default -> current.plusYears(1);
        };
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private Project findProject(String projectCode, UUID firmId) {
        return projectRepository.findByProjectCodeAndFirmId(projectCode, firmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ProjectManagementConstants.PROJECT_NOT_FOUND));
    }

    private Renewal findRenewal(Long renewalId, UUID projectId) {
        return renewalRepository.findById(renewalId)
                .filter(r -> r.getProjectId().equals(projectId) && r.isActive())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ProjectManagementConstants.RENEWAL_NOT_FOUND));
    }

    private UUID getRequiredFirmId() {
        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }

    private final CurrentUserResolver currentUserResolver;
}
