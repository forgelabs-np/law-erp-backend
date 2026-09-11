package com.lawfirm.erp.tenant.service;

import com.lawfirm.erp.dto.Tenant.request.TenantTypeRequest;
import com.lawfirm.erp.dto.Tenant.response.TenantTypeResponse;
import com.lawfirm.erp.tenant.entity.TenantType;
import com.lawfirm.erp.tenant.repository.TenantTypeRepository;
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
public class TenantTypeService {

    private final TenantTypeRepository tenantTypeRepository;

    @Transactional
    public TenantTypeResponse createTenantType(TenantTypeRequest request, UUID adminId) {
        if (tenantTypeRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Tenant type code already exists: " + request.getCode());
        }

        TenantType tenantType = new TenantType();
        tenantType.setName(request.getName());
        tenantType.setCode(request.getCode());
        tenantType.setDescription(request.getDescription());
        tenantType.setActive(request.getIsActive() != null ? request.getIsActive() : true);
        tenantType.setCreatedBy(adminId);
        tenantType = tenantTypeRepository.save(tenantType);

        log.info("Tenant type created: {} by admin: {}", tenantType.getName(), adminId);
        return toResponse(tenantType);
    }

    public List<TenantTypeResponse> getAllTenantTypes() {
        return tenantTypeRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<TenantTypeResponse> getActiveTenantTypes() {
        return tenantTypeRepository.findAll().stream()
                .filter(TenantType::isActive)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public TenantTypeResponse getTenantTypeById(UUID id) {
        TenantType tenantType = tenantTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant type not found: " + id));
        return toResponse(tenantType);
    }

    @Transactional
    public TenantTypeResponse updateTenantType(UUID id, TenantTypeRequest request, UUID adminId) {
        TenantType tenantType = tenantTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant type not found: " + id));

        tenantType.setName(request.getName());
        tenantType.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            tenantType.setActive(request.getIsActive());
        }
        tenantType.setUpdatedBy(adminId);

        tenantType = tenantTypeRepository.save(tenantType);
        log.info("Tenant type updated: {} by admin: {}", tenantType.getName(), adminId);
        return toResponse(tenantType);
    }

    @Transactional
    public void deleteTenantType(UUID id, UUID adminId) {
        TenantType tenantType = tenantTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant type not found: " + id));
        tenantTypeRepository.delete(tenantType);
        log.info("Tenant type deleted: {} by admin: {}", tenantType.getName(), adminId);
    }

    @Transactional
    public TenantTypeResponse toggleTenantTypeStatus(UUID id, UUID adminId) {
        TenantType tenantType = tenantTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant type not found: " + id));
        tenantType.setActive(!tenantType.isActive());
        tenantType.setUpdatedBy(adminId);
        tenantType = tenantTypeRepository.save(tenantType);
        log.info("Tenant type {} status toggled to {} by admin: {}",
                tenantType.getName(), tenantType.isActive(), adminId);
        return toResponse(tenantType);
    }

    private TenantTypeResponse toResponse(TenantType entity) {
        return TenantTypeResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .code(entity.getCode())
                .description(entity.getDescription())
                .isActive(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}