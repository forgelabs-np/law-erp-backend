package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.DuplicateResourceException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.dto.admin.request.PermissionRequest;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.rbac.entity.Permission;
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
    private final CurrentUserResolver currentUserResolver;

    @Transactional
    public PermissionResponse upsert(PermissionRequest request) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        if (adminId == null) {
            throw new BusinessRuleException("Authenticated user not found");
        }

        // Auto-generate code if not provided
        String generatedCode = request.getCode();
        if (generatedCode == null || generatedCode.isEmpty()) {
            generatedCode = request.getModuleCode() + ":" + request.getAction().name();
        }

        Permission permission = findExistingPermission(request);

        if (permission != null) {
            updatePermission(permission, request, adminId);
            log.info("Permission updated: {} by admin: {}", permission.getCode(), adminId);
        } else {
            validateCodeUniqueness(generatedCode);
            permission = createPermission(request, generatedCode, adminId);
            log.info("Permission created: {} by admin: {}", permission.getCode(), adminId);
        }

        permission = permissionRepository.save(permission);
        return toResponse(permission);
    }

    private Permission createPermission(PermissionRequest request, String code, UUID adminId) {
        Permission permission = new Permission();
        permission.setModuleCode(request.getModuleCode());
        permission.setAction(request.getAction());
        permission.setScope(request.getScope());
        permission.setCode(code);
        permission.setDescription(request.getDescription());
        permission.setActive(request.getIsActive() != null ? request.getIsActive() : true);
        permission.setCreatedBy(adminId);
        permission.setCreatedAt(LocalDateTime.now());
        return permission;
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

    private void updatePermission(Permission permission, PermissionRequest request, UUID adminId) {
        permission.setAction(request.getAction());
        permission.setScope(request.getScope());
        permission.setCode(request.getCode());
        permission.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            permission.setActive(request.getIsActive());
        }
        permission.setUpdatedBy(adminId);
        permission.setUpdatedAt(LocalDateTime.now());
    }

    private Permission createPermission(PermissionRequest request, UUID adminId) {
        Permission permission = new Permission();
        permission.setAction(request.getAction());
        permission.setScope(request.getScope());
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

        // Check if permission is assigned to any module
        if (permissionRepository.countModulePermissionsByPermissionId(id) > 0) {
            throw new BusinessRuleException(
                    String.format("Cannot delete permission: %s. It is assigned to modules.", permission.getCode())
            );
        }

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
        return PermissionResponse.builder()
                .id(entity.getId())
                .action(entity.getAction())
                .scope(entity.getScope())
                .code(entity.getCode())
                .description(entity.getDescription())
                .isActive(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}