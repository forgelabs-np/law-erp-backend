package com.lawfirm.erp.firm.controller;

import com.lawfirm.erp.common.constant.FirmConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.firm.response.FirmModuleResponse;
import com.lawfirm.erp.firm.service.FirmModuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/firm/modules")
@RequiredArgsConstructor
@Tag(name = "Firm Module Management", description = "View enabled modules for the firm")
@PreAuthorize("hasRole('FIRM_ADMIN')")
public class FirmModuleController {

    private final FirmModuleService firmModuleService;
    private final ResponseHandler responseHandler;

    @GetMapping
    @Operation(summary = FirmConstants.GET_MY_ENABLED_MODULES_SUMMARY)
    public ResponseEntity<ApiResponse<List<FirmModuleResponse>>> getMyModules() {
        return responseHandler.ok(
                firmModuleService.getMyEnabledModules(),
                "Enabled modules fetched successfully"
        );
    }
}