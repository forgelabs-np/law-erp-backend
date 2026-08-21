package com.lawfirm.erp.modules.projectmanagement.controller;

import com.lawfirm.erp.common.constant.ProjectManagementConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.modules.projectmanagement.dto.request.CreateRenewalTypeRequest;
import com.lawfirm.erp.modules.projectmanagement.dto.response.RenewalTypeResponse;
import com.lawfirm.erp.modules.projectmanagement.service.RenewalTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/renewal-types")
@RequiredArgsConstructor
@Tag(name = ProjectManagementConstants.TAG_RENEWAL_TYPES)
public class RenewalTypeController {

    private final RenewalTypeService renewalTypeService;

    @GetMapping
    @Operation(summary = ProjectManagementConstants.LIST_RENEWAL_TYPES)
    public ApiResponse<List<RenewalTypeResponse>> listTypes() {
        return ApiResponse.success(renewalTypeService.listTypes());
    }

    @PostMapping
    @Operation(summary = ProjectManagementConstants.CREATE_RENEWAL_TYPE)
    public ApiResponse<RenewalTypeResponse> createType(
            @Valid @RequestBody CreateRenewalTypeRequest request) {
        return ApiResponse.success("Type created", renewalTypeService.createType(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = ProjectManagementConstants.UPDATE_RENEWAL_TYPE)
    public ApiResponse<RenewalTypeResponse> updateType(
            @PathVariable Long id,
            @Valid @RequestBody CreateRenewalTypeRequest request) {
        return ApiResponse.success("Type updated", renewalTypeService.updateType(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = ProjectManagementConstants.DELETE_RENEWAL_TYPE)
    public ApiResponse<Void> deleteType(@PathVariable Long id) {
        renewalTypeService.deleteType(id);
        return ApiResponse.success("Type deleted", null);
    }
}
