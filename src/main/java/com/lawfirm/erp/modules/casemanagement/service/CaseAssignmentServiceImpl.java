package com.lawfirm.erp.modules.casemanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.enums.UserType;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.common.repository.UserRepository;
import com.lawfirm.erp.entity.User;
import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.modules.casemanagement.dto.request.AssignCaseRequest;
import com.lawfirm.erp.modules.casemanagement.dto.response.CaseAssignmentResponse;
import com.lawfirm.erp.modules.casemanagement.entity.CaseAssignment;
import com.lawfirm.erp.modules.casemanagement.entity.Matter;
import com.lawfirm.erp.modules.casemanagement.enums.AssignmentRole;
import com.lawfirm.erp.modules.casemanagement.repository.CaseAssignmentRepository;
import com.lawfirm.erp.modules.casemanagement.repository.MatterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaseAssignmentServiceImpl implements CaseAssignmentService {

    private final CaseAssignmentRepository assignmentRepository;
    private final MatterRepository matterRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public CaseAssignmentResponse assign(String matterNumber, AssignCaseRequest request) {
        UUID firmId = getRequiredFirmId();
        Matter matter = findMatter(matterNumber, firmId);
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getUserId()));

        if (!user.getFirm().getId().equals(firmId)) {
            throw new BusinessRuleException("User does not belong to this firm");
        }

        if (user.getUserType() != UserType.FIRM_USER) {
            throw new BusinessRuleException("Only firm employees can be assigned to a matter");
        }

        boolean exists = assignmentRepository.existsByMatterIdAndUserIdAndFirmId(
                matter.getId(), request.getUserId(), firmId);
        if (exists) {
            throw new BusinessRuleException("User is already assigned to this matter");
        }

        CaseAssignment assignment = new CaseAssignment();
        assignment.setFirmId(firmId);
        assignment.setMatterId(matter.getId());
        assignment.setUserId(request.getUserId());
        assignment.setAssignmentRole(request.getAssignmentRole());
        assignment.setActive(true);
        assignment = assignmentRepository.save(assignment);

        auditService.log(AuditAction.MATTER_UPDATED, AuditEntity.MATTER, matter.getId(),
                "Assigned " + user.getFullName() + " (" + request.getAssignmentRole() + ") to matter " + matterNumber);

        return toResponse(assignment, matter, user);
    }

    @Transactional
    public void revoke(String matterNumber, UUID userId) {
        UUID firmId = getRequiredFirmId();
        Matter matter = findMatter(matterNumber, firmId);

        List<CaseAssignment> assignments = assignmentRepository
                .findByMatterIdAndUserIdAndFirmId(matter.getId(), userId, firmId);
        if (assignments.isEmpty()) {
            throw new ResourceNotFoundException("Assignment not found for user " + userId);
        }

        assignmentRepository.deleteAll(assignments);
        auditService.log(AuditAction.MATTER_UPDATED, AuditEntity.MATTER, matter.getId(),
                "Revoked assignment for user " + userId + " from matter " + matterNumber);
    }

    public List<CaseAssignmentResponse> listByMatter(String matterNumber) {
        UUID firmId = getRequiredFirmId();
        Matter matter = findMatter(matterNumber, firmId);
        List<CaseAssignment> assignments = assignmentRepository.findByMatterIdAndFirmId(matter.getId(), firmId);

        List<User> users = userRepository.findAllById(
                assignments.stream().map(CaseAssignment::getUserId).collect(Collectors.toList()));
        var userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));

        return assignments.stream()
                .map(a -> toResponse(a, matter, userMap.get(a.getUserId())))
                .collect(Collectors.toList());
    }

    public List<CaseAssignmentResponse> listByUser(UUID userId) {
        UUID firmId = getRequiredFirmId();
        List<CaseAssignment> assignments = assignmentRepository.findByUserIdAndFirmId(userId, firmId);

        List<Matter> matters = matterRepository.findAllById(
                assignments.stream().map(CaseAssignment::getMatterId).collect(Collectors.toList()));
        var matterMap = matters.stream().collect(Collectors.toMap(Matter::getId, m -> m));

        User user = userRepository.findById(userId).orElse(null);

        return assignments.stream()
                .map(a -> toResponse(a, matterMap.get(a.getMatterId()), user))
                .collect(Collectors.toList());
    }

    public boolean isAssigned(UUID matterId, UUID userId, UUID firmId) {
        return assignmentRepository.existsByMatterIdAndUserIdAndFirmId(matterId, userId, firmId);
    }

    public List<UUID> getAssignedMatterIds(UUID userId, UUID firmId) {
        return assignmentRepository.findMatterIdsByUserIdAndFirmId(userId, firmId);
    }

    private UUID getRequiredFirmId() {
        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }

    private Matter findMatter(String matterNumber, UUID firmId) {
        return matterRepository.findByMatterNumberAndFirmId(matterNumber, firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Matter not found: " + matterNumber));
    }

    private CaseAssignmentResponse toResponse(CaseAssignment a, Matter matter, User user) {
        return CaseAssignmentResponse.builder()
                .id(a.getId())
                .matterId(a.getMatterId())
                .matterNumber(matter != null ? matter.getMatterNumber() : null)
                .matterTitle(matter != null ? matter.getTitle() : null)
                .userId(a.getUserId())
                .userName(user != null ? user.getFullName() : null)
                .assignmentRole(a.getAssignmentRole())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
