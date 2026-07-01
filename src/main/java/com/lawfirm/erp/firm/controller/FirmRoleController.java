package com.lawfirm.erp.firm.controller;

import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.admin.response.RoleResponse;
import com.lawfirm.erp.firm.service.FirmRoleService;
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
@RequestMapping("/api/v1/firm/roles")
@RequiredArgsConstructor
@Tag(name = "Firm Role Management", description = "Firm admin role management APIs")
@PreAuthorize("hasRole('FIRM_ADMIN')")
public class FirmRoleController {

    private final FirmRoleService firmRoleService;
    private final ResponseHandler responseHandler;

    @GetMapping
    @Operation(summary = "Get available roles for this firm")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getFirmRoles() {
        return responseHandler.ok(
                firmRoleService.getFirmRoles(),
                "Firm roles fetched successfully"
        );
    }
}