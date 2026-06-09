package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.common.exception.BusinessRuleException;
import com.lawfirm.erp.common.exception.DuplicateResourceException;
import com.lawfirm.erp.common.exception.ResourceNotFoundException;
import com.lawfirm.erp.dto.admin.request.ModuleRequest;
import com.lawfirm.erp.dto.admin.response.ModuleResponse;
import com.lawfirm.erp.dto.admin.response.PermissionResponse;
import com.lawfirm.erp.rbac.entity.Module;
import com.lawfirm.erp.rbac.mapper.ModuleMapper;
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
public class ModuleService {

    private final ModuleRepository moduleRepository;
    private final PermissionRepository permissionRepository;
    private final ModuleMapper moduleMapper;
    private final CurrentUserResolver currentUserResolver;

    @Transactional
    public ModuleResponse upsertModule(ModuleRequest request) {
        UUID adminId = currentUserResolver.getCurrentUserId();
        if (adminId == null) {
            throw new BusinessRuleException("Authenticated user not found");
        }

        Module module = findExistingModule(request);

        if (module != null) {
            validateNotSystemModule(module, "modify");
            updateModule(module, request, adminId);
            log.info("Module updated: {} by admin: {}", module.getCode(), adminId);
            module = moduleRepository.save(module);
            return convertToCompleteResponse(module);
        } else {
            validateDuplicateModule(request);
            module = createModule(request, adminId);
            log.info("Module created: {} by admin: {}", module.getCode(), adminId);
            module = moduleRepository.save(module);
            return convertToCompleteResponse(module);
        }
    }

    // GET ALL - MyBatis
    public List<ModuleResponse> getAllModules() {
        return moduleMapper.findAllModules();
    }

    // GET ACTIVE - MyBatis
    public List<ModuleResponse> getActiveModules() {
        return moduleMapper.findAllActiveModules();
    }

    // GET BY ID - MyBatis then add permissions
    public ModuleResponse getModuleById(UUID moduleId) {
        ModuleResponse module = moduleMapper.findModuleById(moduleId);
        if (module == null) {
            throw new ResourceNotFoundException(String.format("Module not found with id: %s", moduleId));
        }

        // Add permissions to the response
        List<PermissionResponse> permissions = permissionRepository.findByModuleId(moduleId)
                .stream()
                .map(permission -> PermissionResponse.builder()
                        .id(permission.getId())
                        .action(permission.getAction())
                        .code(permission.getCode())
                        .description(permission.getDescription())
                        .isActive(permission.isActive())
                        .createdAt(permission.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        module.setPermissions(permissions);
        return module;
    }

    @Transactional
    public void deleteModule(UUID moduleId) {
        UUID adminId = currentUserResolver.getCurrentUserId();

        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Module not found with id: %s", moduleId)
                ));

        validateNotSystemModule(module, "delete");

        long permissionCount = permissionRepository.countByModuleId(moduleId);
        if (permissionCount > 0) {
            throw new BusinessRuleException(
                    String.format("Cannot delete module: %s. It has %d permissions assigned.",
                            module.getName(), permissionCount)
            );
        }

        moduleRepository.delete(module);
        log.info("Module deleted: {} by admin: {}", module.getCode(), adminId);
    }

    @Transactional
    public ModuleResponse toggleModuleStatus(UUID moduleId) {
        UUID adminId = currentUserResolver.getCurrentUserId();

        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Module not found with id: %s", moduleId)
                ));

        validateNotSystemModule(module, "toggle status of");

        module.setActive(!module.isActive());
        module.setUpdatedBy(adminId);
        module.setUpdatedAt(LocalDateTime.now());
        module = moduleRepository.save(module);

        log.info("Module {} toggled to {} by admin: {}",
                module.getCode(), module.isActive(), adminId);

        return convertToCompleteResponse(module);
    }

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
        module.setCode(request.getCode());
        module.setDescription(request.getDescription());
        module.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);
        module.setIcon(request.getIcon());
        module.setPath(request.getPath());
        if (request.getIsActive() != null) {
            module.setActive(request.getIsActive());
        }
        module.setUpdatedBy(adminId);
        module.setUpdatedAt(LocalDateTime.now());
    }

    private Module createModule(ModuleRequest request, UUID adminId) {
        Module module = new Module();
        module.setName(request.getName());
        module.setCode(request.getCode());
        module.setDescription(request.getDescription());
        module.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);
        module.setIcon(request.getIcon());
        module.setPath(request.getPath());
        module.setIsSystem(false);
        module.setActive(request.getIsActive() != null ? request.getIsActive() : true);
        module.setCreatedBy(adminId);
        module.setCreatedAt(LocalDateTime.now());
        return module;
    }

    private ModuleResponse convertToCompleteResponse(Module module) {
        List<PermissionResponse> permissions = permissionRepository.findByModuleId(module.getId())
                .stream()
                .map(permission -> PermissionResponse.builder()
                        .id(permission.getId())
                        .action(permission.getAction())
                        .code(permission.getCode())
                        .description(permission.getDescription())
                        .isActive(permission.isActive())
                        .createdAt(permission.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ModuleResponse.builder()
                .id(module.getId())
                .name(module.getName())
                .code(module.getCode())
                .description(module.getDescription())
                .displayOrder(module.getDisplayOrder())
                .icon(module.getIcon())
                .path(module.getPath())
                .isSystem(module.getIsSystem())
                .isActive(module.isActive())
                .createdAt(module.getCreatedAt())
                .updatedAt(module.getUpdatedAt())
                .permissions(permissions)
                .build();
    }
}