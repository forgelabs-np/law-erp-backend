package com.lawfirm.erp.firm.controller;

import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.common.service.SystemConfigService;
import com.lawfirm.erp.auth.security.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Firm admin manages their firm's brand/display configuration.
 *
 * Base path: /api/v1/firm/config
 * Requires: FIRM_ADMIN role
 *
 * Configurable values:
 *   - BRAND_COLOR_PRIMARY    — sidebar, buttons (#1A237E)
 *   - BRAND_COLOR_SECONDARY  — accents (#E3F2FD)
 *   - EMAIL_FOOTER_TEXT      — "Apex Law Associates © 2025"
 *   - EMAIL_SIGNATURE        — default email sign-off
 *   - TIMEZONE               — "Asia/Kathmandu"
 */
@RestController
@RequestMapping("/api/v1/firm/config")
@RequiredArgsConstructor
@Tag(name = "Firm Config", description = "Firm-scoped configuration — brand colors, email footer, timezone")
@PreAuthorize("hasRole('FIRM_ADMIN')")
public class FirmConfigController {

    private final SystemConfigService systemConfigService;
    private final CurrentUserResolver currentUserResolver;
    private final ResponseHandler responseHandler;

    @GetMapping
    @Operation(summary = "Get firm config values", description = "Returns brand colors, email footer, timezone, etc.")
    public ResponseEntity<ApiResponse<Map<String, String>>> getConfig() {
        UUID firmId = getRequiredFirmId();
        return responseHandler.ok(
                systemConfigService.getAllFirm(firmId),
                "Firm config fetched"
        );
    }

    @PutMapping
    @Operation(summary = "Update firm config values", description = "Bulk update brand colors, email footer, timezone, etc.")
    public ResponseEntity<ApiResponse<Void>> updateConfig(
            @RequestBody Map<String, String> config) {
        UUID firmId = getRequiredFirmId();
        systemConfigService.setFirmBulk(firmId, config);
        return responseHandler.ok(null, "Firm config updated");
    }

    private UUID getRequiredFirmId() {
        UUID firmId = currentUserResolver.getCurrentFirmId();
        if (firmId == null) throw new ForbiddenException("Firm context required");
        return firmId;
    }
}
