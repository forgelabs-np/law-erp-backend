package com.lawfirm.erp.superadmin.controller;

import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.common.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Super Admin manages GLOBAL and per-firm SystemConfig values.
 *
 * Endpoints:
 *   GET    /api/v1/super-admin/config              — list all global config
 *   PUT    /api/v1/super-admin/config              — bulk upsert global config
 *   DELETE /api/v1/super-admin/config/{key}        — delete global config key
 *   GET    /api/v1/super-admin/firms/{id}/config   — get firm config
 *   PUT    /api/v1/super-admin/firms/{id}/config   — set firm config values
 */
@RestController
@RequestMapping("/api/v1/super-admin")
@RequiredArgsConstructor
@Tag(name = "Super Admin Config", description = "System configuration management — global and per-firm")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminConfigController {

    private final SystemConfigService systemConfigService;
    private final ResponseHandler responseHandler;

    // ── Global config ────────────────────────────────────────────────────────

    @GetMapping("/config")
    @Operation(summary = "Get all global system config values")
    public ResponseEntity<ApiResponse<Map<String, String>>> getGlobalConfig() {
        return responseHandler.ok(
                systemConfigService.getAllGlobal(),
                "Global config fetched"
        );
    }

    @PutMapping("/config")
    @Operation(summary = "Bulk upsert global system config values")
    public ResponseEntity<ApiResponse<Void>> updateGlobalConfig(
            @RequestBody Map<String, String> config) {
        systemConfigService.setGlobalBulk(config);
        return responseHandler.ok(null, "Global config updated");
    }

    @DeleteMapping("/config/{key}")
    @Operation(summary = "Delete a global config value")
    public ResponseEntity<ApiResponse<Void>> deleteGlobalConfig(
            @PathVariable String key) {
        systemConfigService.deleteGlobal(key);
        return responseHandler.ok(null, "Config key deleted: " + key);
    }

    // ── Per-firm config ──────────────────────────────────────────────────────

    @GetMapping("/firms/{firmId}/config")
    @Operation(summary = "Get all config values for a specific firm")
    public ResponseEntity<ApiResponse<Map<String, String>>> getFirmConfig(
            @PathVariable UUID firmId) {
        return responseHandler.ok(
                systemConfigService.getAllFirm(firmId),
                "Firm config fetched"
        );
    }

    @PutMapping("/firms/{firmId}/config")
    @Operation(summary = "Bulk upsert config values for a specific firm")
    public ResponseEntity<ApiResponse<Void>> updateFirmConfig(
            @PathVariable UUID firmId,
            @RequestBody Map<String, String> config) {
        systemConfigService.setFirmBulk(firmId, config);
        return responseHandler.ok(null, "Firm config updated");
    }
}
