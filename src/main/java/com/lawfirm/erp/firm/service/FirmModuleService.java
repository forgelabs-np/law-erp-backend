package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.dto.firm.request.EnableModuleRequest;
import com.lawfirm.erp.dto.firm.response.FirmModuleResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface FirmModuleService {

    FirmModuleResponse enableModuleForFirm(UUID firmId, EnableModuleRequest request);

    FirmModuleResponse getModuleConfig(UUID firmId, UUID moduleId);

    List<FirmModuleResponse> getFirmModules(UUID firmId);

    List<FirmModuleResponse> getMyEnabledModules();

    boolean isModuleEnabled(UUID firmId, String moduleCode);

    /** Every module code with its resolved access (sub-modules inherit their parent). */
    Map<String, Boolean> resolveEnabledModules(UUID firmId);
}
