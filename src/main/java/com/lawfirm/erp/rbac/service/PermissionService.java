//package com.lawfirm.erp.rbac.service;
//
//import com.lawfirm.erp.dto.admin.request.PermissionRequest;
//import com.lawfirm.erp.dto.admin.response.PermissionResponse;
//import com.lawfirm.erp.rbac.entity.Permission;
//import com.lawfirm.erp.rbac.repository.PermissionRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//import java.util.UUID;
//import java.util.stream.Collectors;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class PermissionService {
//
//    private final PermissionRepository permissionRepository;
//
//    @Transactional
//    public PermissionResponse upsertPermission(PermissionRequest request, UUID adminId) {
//        Permission permission;
//
//        if (request.getId() != null) {
//            permission = permissionRepository.findById(request.getId())
//                    .orElseThrow(() -> new RuntimeException("Permission not found with id: " + request.getId()));
//        } else if (request.getCode() != null && !request.getCode().isEmpty()) {
//            permission = permissionRepository.findByCode(request.getCode()).orElse(null);
//        } else {
//            permission = null;
//        }
//
//        if (permission != null) {
//            permission.setName(request.getName());
//            permission.setDescription(request.getDescription());
//            if (request.getIsActive() != null) {
//                permission.setActive(request.getIsActive());
//            }
//            permission.setModifiedBy(adminId);
//            log.info("Permission updated: {} by admin: {}", permission.getCode(), adminId);
//        } else {
//            if (permissionRepository.existsByCode(request.getCode())) {
//                throw new RuntimeException("Permission code already exists: " + request.getCode());
//            }
//            permission = new Permission();
//            permission.setName(request.getName());
//            permission.setCode(request.getCode());
//            permission.setDescription(request.getDescription());
//            permission.setActive(request.getIsActive() != null ? request.getIsActive() : true);
//            permission.setCreatedBy(adminId);
//            log.info("Permission created: {} by admin: {}", permission.getCode(), adminId);
//        }
//
//        permission = permissionRepository.save(permission);
//        return toResponse(permission);
//    }
//
//    public List<PermissionResponse> getAllPermissions() {
//        return permissionRepository.findAll().stream()
//                .map(this::toResponse)
//                .collect(Collectors.toList());
//    }
//
//    public List<PermissionResponse> getActivePermissions() {
//        return permissionRepository.findAll().stream()
//                .filter(Permission::isActive)
//                .map(this::toResponse)
//                .collect(Collectors.toList());
//    }
//
//    public PermissionResponse getPermissionById(UUID id) {
//        Permission permission = permissionRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Permission not found: " + id));
//        return toResponse(permission);
//    }
//
//    @Transactional
//    public void deletePermission(UUID id, UUID adminId) {
//        Permission permission = permissionRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Permission not found: " + id));
//        permissionRepository.delete(permission);
//        log.info("Permission deleted: {} by admin: {}", permission.getCode(), adminId);
//    }
//
//    @Transactional
//    public PermissionResponse togglePermissionStatus(UUID id, UUID adminId) {
//        Permission permission = permissionRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Permission not found: " + id));
//        permission.setActive(!permission.isActive());
//        permission.setModifiedBy(adminId);
//        permission = permissionRepository.save(permission);
//        log.info("Permission {} toggled to {} by admin: {}",
//                permission.getCode(), permission.isActive(), adminId);
//        return toResponse(permission);
//    }
//
//    private PermissionResponse toResponse(Permission entity) {
//        return PermissionResponse.builder()
//                .id(entity.getId())
//                .name(entity.getName())
//                .code(entity.getCode())
//                .description(entity.getDescription())
//                .isActive(entity.isActive())
//                .createdAt(entity.getCreatedDate())
//                .build();
//    }
//}