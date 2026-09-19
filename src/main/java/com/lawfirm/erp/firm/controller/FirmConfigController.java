package com.lawfirm.erp.firm.controller;

import com.lawfirm.erp.auth.security.CurrentUserResolver;
import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.constant.FirmConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.dto.SystemConfigSettingView;
import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.common.service.FirmConfigService;
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

/** Firm admin manages their firm's brand/display configuration. */
@RestController
@RequestMapping("/api/v1/firm/config")
@RequiredArgsConstructor
@Tag(name = "Firm Config", description = "Firm-scoped configuration — brand colors, email footer, timezone")
@PreAuthorize("hasRole('FIRM_ADMIN')")
public class FirmConfigController {

    private final FirmConfigService firmConfigService;
    private final CurrentUserResolver currentUserResolver;
    private final AuditService auditService;
    private final ResponseHandler responseHandler;
    private final PermissionEvaluator permissionEvaluator;

    @GetMapping
    @Operation(summary = FirmConstants.GET_FIRM_CONFIG_SUMMARY, description = FirmConstants.GET_FIRM_CONFIG_DESCRIPTION)
    public ResponseEntity<ApiResponse<List<SystemConfigSettingView>>> getConfig() {
        permissionEvaluator.require("FIRM_CONFIG:VIEW");
        UUID firmId = getRequiredFirmId();
        return responseHandler.ok(
                firmConfigService.getSettings(firmId),
                "Firm config fetched"
        );
    }

    @PutMapping
    @Operation(summary = FirmConstants.UPDATE_FIRM_CONFIG_SUMMARY, description = FirmConstants.UPDATE_FIRM_CONFIG_DESCRIPTION)
    public ResponseEntity<ApiResponse<Void>> updateConfig(
            @RequestBody Map<String, String> config) {
        permissionEvaluator.require("FIRM_CONFIG:EDIT");
        UUID firmId = getRequiredFirmId();
        firmConfigService.setBulk(firmId, config);
        auditService.log(
                AuditAction.CONFIG_UPDATED,
                AuditEntity.SYSTEM_CONFIG,
                firmId,
                "Firm config updated: " + config.keySet()
        );
        return responseHandler.ok(null, "Firm config updated");
    }

    private UUID getRequiredFirmId() {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }
}
