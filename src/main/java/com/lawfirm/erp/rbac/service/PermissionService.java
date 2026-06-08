package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.dto.admin.request.PermissionRequest;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.security.CurrentUserResolver;
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
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final CurrentUserResolver currentUserResolver;

    @Transactional
    public PermissionResponse upsert(PermissionRequest request) {
        UUID adminId = currentUserResolver.getCurrentUserId();

        Permission permission = findExistingPermission(request);

        if (permission != null) {
            updatePermission(permission, request, adminId);
            log.info("Permission updated: {} by admin: {}", permission.getCode(), adminId);
        } else {
            validateCodeUniqueness(request.getCode());
            permission = createPermission(request, adminId);
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

    private void updatePermission(Permission permission, PermissionRequest request, UUID adminId) {
        permission.setModule(request.getModule());
        permission.setAction(request.getAction());
        permission.setCode(request.getCode());
        permission.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            permission.setActive(request.getIsActive());
        }
        permission.setUpdatedBy(adminId);
    }

    private Permission createPermission(PermissionRequest request, UUID adminId) {
        Permission permission = new Permission();
        permission.setModule(request.getModule());
        permission.setAction(request.getAction());
        permission.setCode(request.getCode());
        permission.setDescription(request.getDescription());
        permission.setActive(request.getIsActive() != null ? request.getIsActive() : true);
        permission.setCreatedBy(adminId);
        return permission;
    }

    private void validateCodeUniqueness(String code) {
        if (permissionRepository.existsByCode(code)) {
            throw new RuntimeException("Permission code already exists: " + code);
        }
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
                .orElseThrow(() -> new RuntimeException("Permission not found: " + id));
        return toResponse(permission);
    }

    @Transactional
    public void delete(UUID id) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Permission not found: " + id));
        permissionRepository.delete(permission);
        log.info("Permission deleted: {} by admin: {}", permission.getCode(), adminId);
    }

    @Transactional
    public PermissionResponse toggleStatus(UUID id) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Permission not found: " + id));
        permission.setActive(!permission.isActive());
        permission.setUpdatedBy(adminId);
        permission = permissionRepository.save(permission);
        log.info("Permission {} toggled to {} by admin: {}",
                permission.getCode(), permission.isActive(), adminId);
        return toResponse(permission);
    }

    private PermissionResponse toResponse(Permission entity) {
        return PermissionResponse.builder()
                .id(entity.getId())
                .module(entity.getModule())
                .action(entity.getAction())
                .code(entity.getCode())
                .description(entity.getDescription())
                .isActive(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}