package com.lawfirm.erp.rbac.service;

import com.lawfirm.erp.dto.admin.request.ModuleRequest;
import com.lawfirm.erp.dto.admin.response.ModuleResponse;

import java.util.List;
import java.util.UUID;

public interface ModuleService {

    ModuleResponse upsertModule(ModuleRequest request);

    ModuleResponse assignPermissionsToModule(UUID moduleId, List<UUID> permissionIds);

    List<ModuleResponse> getAllModules();

    List<ModuleResponse> getActiveModules();

    ModuleResponse getModuleById(UUID moduleId);

    List<ModuleResponse> getAllModulesFlat();

    void deleteModule(UUID moduleId);

    ModuleResponse toggleModuleStatus(UUID moduleId);
}
