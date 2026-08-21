package com.lawfirm.erp.modules.projectmanagement.service;

import com.lawfirm.erp.auth.security.FirmContextHolder;
import com.lawfirm.erp.common.constant.ProjectManagementConstants;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.modules.projectmanagement.dto.request.CreateRenewalTypeRequest;
import com.lawfirm.erp.modules.projectmanagement.dto.response.RenewalTypeResponse;
import com.lawfirm.erp.modules.projectmanagement.entity.RenewalType;
import com.lawfirm.erp.modules.projectmanagement.mapper.ProjectMapper;
import com.lawfirm.erp.modules.projectmanagement.repository.RenewalTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RenewalTypeServiceImpl implements RenewalTypeService {

    private final RenewalTypeRepository renewalTypeRepository;
    private final ProjectMapper projectMapper;

    public List<RenewalTypeResponse> listTypes() {
        UUID firmId = getRequiredFirmId();
        return renewalTypeRepository.findAvailableForFirm(firmId).stream()
                .map(projectMapper::toRenewalTypeResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public RenewalTypeResponse createType(CreateRenewalTypeRequest request) {
        UUID firmId = getRequiredFirmId();

        if (renewalTypeRepository.existsByNameAndFirmIdAndActive(request.getName(), firmId, true)) {
            throw new BusinessRuleException("Renewal type with this name already exists");
        }

        RenewalType type = RenewalType.builder()
                .firmId(firmId)
                .name(request.getName())
                .description(request.getDescription())
                .system(false)
                .active(true)
                .build();
        type = renewalTypeRepository.save(type);
        return projectMapper.toRenewalTypeResponse(type);
    }

    @Transactional
    public RenewalTypeResponse updateType(Long typeId, CreateRenewalTypeRequest request) {
        RenewalType type = findCustomType(typeId);
        if (request.getName() != null) type.setName(request.getName());
        if (request.getDescription() != null) type.setDescription(request.getDescription());
        type = renewalTypeRepository.save(type);
        return projectMapper.toRenewalTypeResponse(type);
    }

    @Transactional
    public void deleteType(Long typeId) {
        RenewalType type = findCustomType(typeId);
        type.setActive(false);
        renewalTypeRepository.save(type);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private RenewalType findCustomType(Long typeId) {
        RenewalType type = renewalTypeRepository.findById(typeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ProjectManagementConstants.RENEWAL_TYPE_NOT_FOUND));
        if (type.isSystem()) {
            throw new BusinessRuleException(ProjectManagementConstants.CANNOT_DELETE_SYSTEM_TYPE);
        }
        UUID firmId = getRequiredFirmId();
        if (type.getFirmId() == null || !type.getFirmId().equals(firmId)) {
            throw new ForbiddenException("Renewal type does not belong to this firm");
        }
        return type;
    }

    private UUID getRequiredFirmId() {
        UUID firmId = FirmContextHolder.getFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }
}
