package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.firm.entity.FirmModule;
import com.lawfirm.erp.rbac.entity.Module;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ModuleAccessResolver {

    private ModuleAccessResolver() {
    }

    public static Map<UUID, FirmModule> indexByModuleId(List<FirmModule> rows) {
        Map<UUID, FirmModule> byModuleId = new HashMap<>();
        for (FirmModule row : rows) {
            byModuleId.put(row.getModule().getId(), row);
        }
        return byModuleId;
    }

    public static boolean isEnabled(Module module, Map<UUID, FirmModule> rowsByModuleId) {
        return isEnabled(module, rowsByModuleId, LocalDateTime.now());
    }

    public static boolean isEnabled(Module module, Map<UUID, FirmModule> rowsByModuleId, LocalDateTime now) {
        Module current = module;
        while (current != null) {
            FirmModule row = rowsByModuleId.get(current.getId());
            if (row != null) {
                return Boolean.TRUE.equals(row.getIsEnabled())
                        && (row.getExpiresAt() == null || row.getExpiresAt().isAfter(now));
            }
            current = current.getParent();
        }
        return false;
    }
}
