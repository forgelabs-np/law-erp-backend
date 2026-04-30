package com.lawfirm.erp.service;

import com.lawfirm.erp.dto.Tenant.request.TenantTypeRequest;
import com.lawfirm.erp.dto.Tenant.response.TenantTypeResponse;
import com.lawfirm.erp.entity.TenantType;
import com.lawfirm.erp.repository.TenantTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantTypeService {

    private final TenantTypeRepository tenantTypeRepository;

    @Transactional
    public TenantTypeResponse createTenantType(TenantTypeRequest request, Long adminId) {
        if (tenantTypeRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Tenant type code already exists: " + request.getCode());
        }

        TenantType tenantType = new TenantType();
        tenantType.setName(request.getName());
        tenantType.setCode(request.getCode());
        tenantType.setDescription(request.getDescription());
        tenantType.setActive(request.getIsActive() != null ? request.getIsActive() : true);
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
        // Use isActive() from parent class
        return tenantTypeRepository.findAll().stream()
                .filter(TenantType::isActive)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public TenantTypeResponse getTenantTypeById(Long id) {
        TenantType tenantType = tenantTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant type not found: " + id));
        return toResponse(tenantType);
    }

    @Transactional
    public TenantTypeResponse updateTenantType(Long id, TenantTypeRequest request, Long adminId) {
        TenantType tenantType = tenantTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant type not found: " + id));

        tenantType.setName(request.getName());
        tenantType.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            tenantType.setActive(request.getIsActive());
        }

        tenantType = tenantTypeRepository.save(tenantType);
        log.info("Tenant type updated: {} by admin: {}", tenantType.getName(), adminId);
        return toResponse(tenantType);
    }

    @Transactional
    public void deleteTenantType(Long id, Long adminId) {
        TenantType tenantType = tenantTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant type not found: " + id));
        tenantTypeRepository.delete(tenantType);
        log.info("Tenant type deleted: {} by admin: {}", tenantType.getName(), adminId);
    }

    @Transactional
    public TenantTypeResponse toggleTenantTypeStatus(Long id, Long adminId) {
        TenantType tenantType = tenantTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant type not found: " + id));
        tenantType.setActive(!tenantType.isActive());  // Use setActive() and isActive()
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
                .createdDate(entity.getCreatedDate())
                .build();
    }
}