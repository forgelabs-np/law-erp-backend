package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.DuplicateResourceException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.dto.admin.request.PermissionRequest;
import com.lawfirm.erp.dto.admin.response.ModuleResponse;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.rbac.entity.Module;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.repository.ModuleRepository;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final ModuleRepository moduleRepository;
    private final CurrentUserResolver currentUserResolver;

    @Transactional
    public PermissionResponse upsert(PermissionRequest request) {
        UUID adminId = currentUserResolver.getCurrentUserId();

        // Get the module
        Module module = moduleRepository.findById(request.getModuleId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Module not found with id: %s", request.getModuleId())
                ));

        Permission permission = findExistingPermission(request);

        if (permission != null) {
            updatePermission(permission, request, module, adminId);
            log.info("Permission updated: {} by admin: {}", permission.getCode(), adminId);
        } else {
            validateCodeUniqueness(request.getCode());
            validateModuleActionUniqueness(module.getId(), request.getAction());
            permission = createPermission(request, module, adminId);
            log.info("Permission created: {} by admin: {}", permission.getCode(), adminId);
        }

        permission = permissionRepository.save(permission);
        return toResponse(permission);
    }

    private Permission findExistingPermission(PermissionRequest request) {
        if (request.getId() != null) {
            return permissionRepository.findById(request.getId()).orElse(null);
        }
        if (request.getCode() != null && !request.getCode().isEmpty()) {
            return permissionRepository.findByCode(request.getCode()).orElse(null);
        }
        return null;
    }

    private void validateCodeUniqueness(String code) {
        if (permissionRepository.existsByCode(code)) {
            throw new DuplicateResourceException("Permission code already exists: " + code);
        }
    }

    private void validateModuleActionUniqueness(UUID moduleId, com.lawfirm.erp.common.enums.PermissionAction action) {
        if (permissionRepository.existsByModuleIdAndAction(moduleId, action)) {
            throw new DuplicateResourceException(
                    String.format("Permission with action %s already exists for this module", action)
            );
        }
    }

    private void updatePermission(Permission permission, PermissionRequest request, Module module, UUID adminId) {
        permission.setModule(module);
        permission.setAction(request.getAction());
        permission.setCode(request.getCode());
        permission.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            permission.setActive(request.getIsActive());
        }
        permission.setUpdatedBy(adminId);
        permission.setUpdatedAt(LocalDateTime.now());
    }

    private Permission createPermission(PermissionRequest request, Module module, UUID adminId) {
        Permission permission = new Permission();
        permission.setModule(module);
        permission.setAction(request.getAction());
        permission.setCode(request.getCode());
        permission.setDescription(request.getDescription());
        permission.setActive(request.getIsActive() != null ? request.getIsActive() : true);
        permission.setCreatedBy(adminId);
        permission.setCreatedAt(LocalDateTime.now());
        return permission;
    }

    public List<PermissionResponse> findAll() {
        return permissionRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<PermissionResponse> findActive() {
        return permissionRepository.findAll().stream()
                .filter(Permission::isActive)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public PermissionResponse findById(UUID id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + id));
        return toResponse(permission);
    }

    @Transactional
    public void delete(UUID id) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + id));

        // Check if permission is assigned to any role
        if (permissionRepository.countRolePermissionsByPermissionId(id) > 0) {
            throw new BusinessRuleException(
                    String.format("Cannot delete permission: %s. It is assigned to roles.", permission.getCode())
            );
        }

        permissionRepository.delete(permission);
        log.info("Permission deleted: {} by admin: {}", permission.getCode(), adminId);
    }

    @Transactional
    public PermissionResponse toggleStatus(UUID id) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + id));
        permission.setActive(!permission.isActive());
        permission.setUpdatedBy(adminId);
        permission.setUpdatedAt(LocalDateTime.now());
        permission = permissionRepository.save(permission);
        log.info("Permission {} toggled to {} by admin: {}",
                permission.getCode(), permission.isActive(), adminId);
        return toResponse(permission);
    }

    private PermissionResponse toResponse(Permission entity) {
        ModuleResponse moduleResponse = ModuleResponse.builder()
                .id(entity.getModule().getId())
                .name(entity.getModule().getName())
                .code(entity.getModule().getCode())
                .isActive(entity.getModule().isActive())
                .build();

        return PermissionResponse.builder()
                .id(entity.getId())
                .module(moduleResponse)
                .action(entity.getAction())
                .code(entity.getCode())
                .description(entity.getDescription())
                .isActive(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}