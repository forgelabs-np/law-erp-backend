package com.lawfirm.erp.firm.service;

import com.lawfirm.erp.firm.entity.FirmModule;
import com.lawfirm.erp.rbac.entity.Module;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How a firm's module access resolves down the module tree.
 *
 * A sub-module has no toggle of its own until one is written, so access is inherited:
 * the nearest ancestor-or-self FirmModule row decides. Enabling TESTCONFIG therefore
 * grants TESTCONFIG 1 / TESTCONFIG 2, and explicitly disabling a child still wins over
 * its enabled parent. This is the single rule used by the sidebar (/me), the API guard
 * (PermissionEvaluator) and the enable/disable endpoint.
 */
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

    /** Nearest ancestor-or-self row decides; no row anywhere means not enabled. */
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
