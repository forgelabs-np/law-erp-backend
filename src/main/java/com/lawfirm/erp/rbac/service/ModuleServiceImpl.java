package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.modules.audit.service.AuditService;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.DuplicateResourceException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.dto.admin.request.ModuleRequest;
import com.lawfirm.erp.dto.admin.response.ModuleResponse;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.rbac.entity.Module;
import com.lawfirm.erp.rbac.entity.ModulePermission;
import com.lawfirm.erp.rbac.entity.Permission;
import com.lawfirm.erp.rbac.mapper.ModuleMapper;
import com.lawfirm.erp.rbac.repository.ModulePermissionRepository;
import com.lawfirm.erp.rbac.repository.ModuleRepository;
import com.lawfirm.erp.rbac.repository.PermissionRepository;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModuleServiceImpl implements ModuleService {

    private final ModuleRepository moduleRepository;
    private final PermissionRepository permissionRepository;
    private final ModuleMapper moduleMapper;
    private final CurrentUserResolver currentUserResolver;
    private final ModulePermissionRepository modulePermissionRepository;
    private final AuditService auditService;
    private final com.lawfirm.erp.rbac.mapper.RbacResponseMapper rbacResponseMapper;

    @Transactional
    @Override
    public ModuleResponse upsertModule(ModuleRequest request) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        if (adminId == null) {
            throw new BusinessRuleException("Authenticated user not found");
        }

        Module existingModule = findExistingModule(request);
        final Module module;

        if (existingModule != null) {
            validateNotSystemModule(existingModule, "modify");
            updateModule(existingModule, request, adminId);
            log.info("Module updated: {} by admin: {}", existingModule.getCode(), adminId);
            module = moduleRepository.save(existingModule);

            // ✅ AUDIT: Module updated
            auditService.log(
                    AuditAction.MODULE_UPDATED,
                    AuditEntity.MODULE,
                    module.getId(),
                    "Module updated: " + module.getCode() + " (" + module.getName() + ")"
            );
        } else {
            validateDuplicateModule(request);
            Module newModule = createModule(request, adminId);
            log.info("Module created: {} by admin: {}", newModule.getCode(), adminId);
            module = moduleRepository.save(newModule);

            // ✅ AUDIT: Module created
            auditService.log(
                    AuditAction.MODULE_CREATED,
                    AuditEntity.MODULE,
                    module.getId(),
                    "Module created: " + module.getCode() + " (" + module.getName() + ")"
            );
        }

        // Handle permission assignments using junction table
        if (request.getPermissionIds() != null) {
            modulePermissionRepository.deleteByModuleId(module.getId());

            if (!request.getPermissionIds().isEmpty()) {
                List<Permission> permissions = permissionRepository.findAllById(request.getPermissionIds());

                List<ModulePermission> modulePermissions = permissions.stream()
                        .map(permission -> ModulePermission.builder()
                                .module(module)
                                .permission(permission)
                                .build())
                        .collect(Collectors.toList());

                modulePermissionRepository.saveAll(modulePermissions);
                log.info("Assigned {} permissions to module: {}", permissions.size(), module.getCode());
            }
        }

        return convertToCompleteResponse(module);
    }

    @Transactional
    @Override
    public ModuleResponse assignPermissionsToModule(UUID moduleId, List<UUID> permissionIds) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        if (adminId == null) {
            throw new BusinessRuleException("Authenticated user not found");
        }

        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + moduleId));

        validateNotSystemModule(module, "modify permissions of");

        modulePermissionRepository.deleteByModuleId(moduleId);

        if (permissionIds != null && !permissionIds.isEmpty()) {
            List<Permission> permissions = permissionRepository.findAllById(permissionIds);

            if (permissions.size() != permissionIds.size()) {
                throw new ResourceNotFoundException("One or more permission IDs are invalid");
            }

            List<ModulePermission> modulePermissions = permissions.stream()
                    .map(permission -> ModulePermission.builder()
                            .module(module)
                            .permission(permission)
                            .build())
                    .collect(Collectors.toList());

            modulePermissionRepository.saveAll(modulePermissions);
            log.info("Assigned {} permissions to module: {}", permissions.size(), module.getCode());

            // ✅ AUDIT: Permissions assigned to module
            auditService.log(
                    AuditAction.ROLE_PERMISSION_CHANGED,
                    AuditEntity.MODULE,
                    module.getId(),
                    "Assigned " + permissions.size() + " permissions to module: " + module.getCode()
            );
        }

        return convertToCompleteResponse(module);
    }

    @Override
    public List<ModuleResponse> getAllModules() {
        List<ModuleResponse> allModules = moduleMapper.findAllModules();
        return buildModuleTree(allModules);
    }

    private List<ModuleResponse> buildModuleTree(List<ModuleResponse> flatModules) {
        Map<UUID, ModuleResponse> moduleMap = new HashMap<>();

        for (ModuleResponse module : flatModules) {
            moduleMap.put(module.getId(), module);
            module.setSubModules(new ArrayList<>());
        }

        List<ModuleResponse> rootModules = new ArrayList<>();
        for (ModuleResponse module : flatModules) {
            if (module.getParentId() != null && moduleMap.containsKey(module.getParentId())) {
                ModuleResponse parent = moduleMap.get(module.getParentId());
                parent.getSubModules().add(module);
            } else {
                rootModules.add(module);
            }
        }
        return rootModules;
    }

    @Override
    public List<ModuleResponse> getActiveModules() {
        List<ModuleResponse> allModules = moduleMapper.findAllActiveModules();
        return buildModuleTree(allModules);
    }

    @Override
    public ModuleResponse getModuleById(UUID moduleId) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Module not found with id: %s", moduleId)
                ));
        return convertToCompleteResponseWithSubModules(module);
    }

    @Override
    public List<ModuleResponse> getAllModulesFlat() {
        return moduleMapper.findAllModulesFlat();
    }

    @Transactional
    @Override
    public void deleteModule(UUID moduleId) {
        UUID adminId = currentUserResolver.getCurrentUserId();

        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Module not found with id: %s", moduleId)
                ));

        validateNotSystemModule(module, "delete");

        if (module.getSubModules() != null && !module.getSubModules().isEmpty()) {
            throw new BusinessRuleException(
                    String.format("Cannot delete module: %s. It has %d sub-modules. Delete sub-modules first.",
                            module.getName(), module.getSubModules().size())
            );
        }

        long permissionCount = modulePermissionRepository.countByModuleId(moduleId);
        if (permissionCount > 0) {
            throw new BusinessRuleException(
                    String.format("Cannot delete module: %s. It has %d permissions assigned. Remove permissions first.",
                            module.getName(), permissionCount)
            );
        }

        moduleRepository.delete(module);
        log.info("Module deleted: {} by admin: {}", module.getCode(), adminId);

        // ✅ AUDIT: Module deleted
        auditService.log(
                AuditAction.MODULE_DELETED,
                AuditEntity.MODULE,
                module.getId(),
                "Module deleted: " + module.getCode()
        );
    }

    @Transactional
    @Override
    public ModuleResponse toggleModuleStatus(UUID moduleId) {
        UUID adminId = currentUserResolver.getCurrentUserId();

        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));

        validateNotSystemModule(module, "toggle status of");

        boolean newStatus = !module.isActive();
        module.setActive(newStatus);
        module.setUpdatedBy(adminId);
        module.setUpdatedAt(LocalDateTime.now());
        module = moduleRepository.save(module);

        if (!newStatus && module.getSubModules() != null) {
            for (Module child : module.getSubModules()) {
                child.setActive(false);
                child.setUpdatedBy(adminId);
                child.setUpdatedAt(LocalDateTime.now());
                moduleRepository.save(child);
            }
            log.info("Disabled {} child modules of {}", module.getSubModules().size(), module.getCode());
        }

        // ✅ AUDIT: Module status toggled
        auditService.log(
                newStatus ? AuditAction.MODULE_ACTIVATED : AuditAction.MODULE_DEACTIVATED,
                AuditEntity.MODULE,
                module.getId(),
                "Module " + module.getCode() + " toggled to: " + (newStatus ? "active" : "inactive")
        );

        return convertToCompleteResponse(module);
    }

    // ─── Private Methods ─────────────────────────────────────────────────────

    private Module findExistingModule(ModuleRequest request) {
        if (request.getId() != null) {
            return moduleRepository.findById(request.getId()).orElse(null);
        }
        if (request.getCode() != null && !request.getCode().isEmpty()) {
            return moduleRepository.findByCode(request.getCode()).orElse(null);
        }
        return null;
    }

    private void validateNotSystemModule(Module module, String operation) {
        if (module.getIsSystem() != null && module.getIsSystem()) {
            throw new BusinessRuleException(
                    String.format("Cannot %s system module: %s", operation, module.getName())
            );
        }
    }

    private void validateDuplicateModule(ModuleRequest request) {
        if (moduleRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException(
                    String.format("Module name already exists: %s", request.getName())
            );
        }
        if (moduleRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException(
                    String.format("Module code already exists: %s", request.getCode())
            );
        }
    }

    private void updateModule(Module module, ModuleRequest request, UUID adminId) {
        module.setName(request.getName());
        module.setCode(request.getCode().toUpperCase());
        module.setDescription(request.getDescription());
        module.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);
        module.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        module.setIcon(request.getIcon());
        module.setPath(request.getPath());

        if (request.getParentId() != null) {
            Module parent = moduleRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent module not found"));
            module.setParent(parent);
            module.setLevel(parent.getLevel() + 1);
        } else {
            module.setParent(null);
            module.setLevel(0);
        }

        if (request.getIsActive() != null) {
            module.setActive(request.getIsActive());
        }
        module.setUpdatedBy(adminId);
        module.setUpdatedAt(LocalDateTime.now());
    }

    private Module createModule(ModuleRequest request, UUID adminId) {
        Module module = new Module();
        module.setName(request.getName());
        module.setCode(request.getCode().toUpperCase());
        module.setDescription(request.getDescription());
        module.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);
        module.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        module.setIcon(request.getIcon());
        module.setPath(request.getPath());

        if (request.getParentId() != null) {
            Module parent = moduleRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent module not found"));
            module.setParent(parent);
            module.setLevel(parent.getLevel() + 1);
        } else {
            module.setParent(null);
            module.setLevel(0);
        }

        module.setIsSystem(false);
        module.setActive(request.getIsActive() != null ? request.getIsActive() : true);
        module.setCreatedBy(adminId);
        module.setCreatedAt(LocalDateTime.now());
        return module;
    }

    private ModuleResponse convertToCompleteResponse(Module module) {
        List<PermissionResponse> permissions = rbacResponseMapper.toPermissionResponseList(
                modulePermissionRepository.findPermissionsByModuleId(module.getId()));

        return ModuleResponse.builder()
                .id(module.getId())
                .name(module.getName())
                .code(module.getCode())
                .description(module.getDescription())
                .level(module.getLevel())
                .displayOrder(module.getDisplayOrder())
                .sortOrder(module.getSortOrder())
                .icon(module.getIcon())
                .path(module.getPath())
                .isSystem(module.getIsSystem())
                .isActive(module.isActive())
                .createdAt(module.getCreatedAt())
                .updatedAt(module.getUpdatedAt())
                .permissions(permissions)
                .build();
    }

    private ModuleResponse convertToCompleteResponseWithSubModules(Module module) {
        List<PermissionResponse> permissions = rbacResponseMapper.toPermissionResponseList(
                modulePermissionRepository.findPermissionsByModuleId(module.getId()));

        List<ModuleResponse> subModuleResponses = new ArrayList<>();
        if (module.getSubModules() != null && !module.getSubModules().isEmpty()) {
            subModuleResponses = module.getSubModules().stream()
                    .map(this::convertToMinimalResponse)
                    .collect(Collectors.toList());
        }

        return ModuleResponse.builder()
                .id(module.getId())
                .name(module.getName())
                .code(module.getCode())
                .description(module.getDescription())
                .level(module.getLevel())
                .displayOrder(module.getDisplayOrder())
                .sortOrder(module.getSortOrder())
                .icon(module.getIcon())
                .path(module.getPath())
                .isSystem(module.getIsSystem())
                .isActive(module.isActive())
                .createdAt(module.getCreatedAt())
                .updatedAt(module.getUpdatedAt())
                .permissions(permissions)
                .subModules(subModuleResponses)
                .build();
    }

    private ModuleResponse convertToMinimalResponse(Module module) {
        return ModuleResponse.builder()
                .id(module.getId())
                .name(module.getName())
                .code(module.getCode())
                .description(module.getDescription())
                .level(module.getLevel())
                .displayOrder(module.getDisplayOrder())
                .sortOrder(module.getSortOrder())
                .icon(module.getIcon())
                .path(module.getPath())
                .isSystem(module.getIsSystem())
                .isActive(module.isActive())
                .createdAt(module.getCreatedAt())
                .updatedAt(module.getUpdatedAt())
                .build();
    }
}