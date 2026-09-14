package com.lawfirm.erp.superadmin.controller;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.common.constant.SuperAdminConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.dto.SystemConfigSettingView;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.modules.audit.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Super Admin manages GLOBAL and per-firm SystemConfig values. */
@RestController
@RequestMapping("/api/v1/super-admin")
@RequiredArgsConstructor
@Tag(name = "Super Admin Config", description = "System configuration management — global and per-firm")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminConfigController {

    private final SystemConfigService systemConfigService;
    private final AuditService auditService;
    private final CurrentUserResolver currentUserResolver;
    private final ResponseHandler responseHandler;

    // Global config

    @GetMapping("/config")
    @Operation(summary = SuperAdminConstants.GET_GLOBAL_CONFIG_SUMMARY,
            description = "Returns all active global settings with metadata (group, input type, allowed values) so a SETTINGS UI can render and validate each key. Values are decrypted for the admin.")
    public ResponseEntity<ApiResponse<List<SystemConfigSettingView>>> getGlobalConfig() {
        return responseHandler.ok(
                systemConfigService.getGlobalSettings(),
                "Global config fetched"
        );
    }

    @PutMapping("/config")
    @Operation(summary = SuperAdminConstants.UPDATE_GLOBAL_CONFIG_SUMMARY)
    public ResponseEntity<ApiResponse<Void>> updateGlobalConfig(
            @RequestBody Map<String, String> config) {
        systemConfigService.setGlobalBulk(config);
        auditService.log(
                AuditAction.CONFIG_UPDATED,
                AuditEntity.SYSTEM_CONFIG,
                null,
                "Global config updated: " + config.keySet()
        );
        return responseHandler.ok(null, "Global config updated");
    }

    @DeleteMapping("/config/{key}")
    @Operation(summary = SuperAdminConstants.DELETE_GLOBAL_CONFIG_SUMMARY)
    public ResponseEntity<ApiResponse<Void>> deleteGlobalConfig(
            @PathVariable String key) {
        systemConfigService.deleteGlobal(key);
        auditService.log(
                AuditAction.CONFIG_DELETED,
                AuditEntity.SYSTEM_CONFIG,
                null,
                "Global config key deleted: " + key
        );
        return responseHandler.ok(null, "Config key deleted: " + key);
    }

    // Per-firm config

    @GetMapping("/firms/{firmId}/config")
    @Operation(summary = SuperAdminConstants.GET_FIRM_CONFIG_SUMMARY,
            description = "Returns all active settings for a firm with metadata (group, input type, allowed values). Values are decrypted for the admin.")
    public ResponseEntity<ApiResponse<List<SystemConfigSettingView>>> getFirmConfig(
            @PathVariable UUID firmId) {
        return responseHandler.ok(
                systemConfigService.getFirmSettings(firmId),
                "Firm config fetched"
        );
    }

    @PutMapping("/firms/{firmId}/config")
    @Operation(summary = SuperAdminConstants.UPDATE_FIRM_CONFIG_SUMMARY)
    public ResponseEntity<ApiResponse<Void>> updateFirmConfig(
            @PathVariable UUID firmId,
            @RequestBody Map<String, String> config) {
        systemConfigService.setFirmBulk(firmId, config);
        auditService.logExplicit(
                firmId,
                currentUserResolver.getCurrentUserId(),
                "S",
                AuditAction.CONFIG_UPDATED,
                AuditEntity.SYSTEM_CONFIG,
                firmId,
                "Firm config updated for firm " + firmId + ": " + config.keySet(),
                null
        );
        return responseHandler.ok(null, "Firm config updated");
    }
}
