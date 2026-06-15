package com.lawfirm.erp.superadmin.controller;

import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.dto.firm.request.CreateFirmRequest;
import com.lawfirm.erp.dto.firm.response.FirmCreationResponse;
import com.lawfirm.erp.firm.service.FirmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/super-admin/firms")
@RequiredArgsConstructor
@Tag(name = "Super Admin - Firm Management", description = "Super Admin firm management APIs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class FirmAdminController {

    private final FirmService firmService;
    private final ResponseHandler responseHandler;

    @PostMapping
    @Operation(summary = "Create a new firm with Firm Admin")
    public ResponseEntity<ApiResponse<FirmCreationResponse>> createFirm(
            @Valid @RequestBody ApiRequest<CreateFirmRequest> request) {
        return responseHandler.ok(
                firmService.createFirm(request.getData()),
                "Firm created successfully"
        );
    }
}