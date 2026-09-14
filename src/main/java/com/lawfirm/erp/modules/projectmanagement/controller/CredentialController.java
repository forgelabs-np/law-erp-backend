package com.lawfirm.erp.modules.projectmanagement.controller;

import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.constant.ProjectManagementConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.modules.projectmanagement.dto.request.*;
import com.lawfirm.erp.modules.projectmanagement.dto.response.CredentialResponse;
import com.lawfirm.erp.modules.projectmanagement.service.CredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects/{projectCode}/credentials")
@RequiredArgsConstructor
@Tag(name = ProjectManagementConstants.TAG_CREDENTIALS)
public class CredentialController {

    private final CredentialService credentialService;
    private final PermissionEvaluator permissionEvaluator;

    @PostMapping
    @Operation(summary = ProjectManagementConstants.ADD_CREDENTIAL)
    public ApiResponse<CredentialResponse> addCredential(
            @PathVariable String projectCode,
            @Valid @RequestBody AddCredentialRequest request) {
        permissionEvaluator.require("PROJECT_MANAGEMENT:EDIT");
        return ApiResponse.success("Credential added",
                credentialService.addCredential(projectCode, request));
    }

    @GetMapping
    @Operation(summary = ProjectManagementConstants.LIST_CREDENTIALS)
    public ApiResponse<List<CredentialResponse>> listCredentials(@PathVariable String projectCode) {
        permissionEvaluator.require("PROJECT_MANAGEMENT:CREDENTIAL_VIEW");
        return ApiResponse.success(credentialService.listCredentials(projectCode));
    }

    @GetMapping("/{id}")
    @Operation(summary = ProjectManagementConstants.GET_CREDENTIAL)
    public ApiResponse<CredentialResponse> getCredential(
            @PathVariable String projectCode,
            @PathVariable Long id) {
        permissionEvaluator.require("PROJECT_MANAGEMENT:CREDENTIAL_VIEW");
        return ApiResponse.success(credentialService.getCredential(projectCode, id));
    }

    @PutMapping("/{id}")
    @Operation(summary = ProjectManagementConstants.UPDATE_CREDENTIAL)
    public ApiResponse<CredentialResponse> updateCredential(
            @PathVariable String projectCode,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCredentialRequest request) {
        permissionEvaluator.require("PROJECT_MANAGEMENT:EDIT");
        return ApiResponse.success("Credential updated",
                credentialService.updateCredential(projectCode, id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = ProjectManagementConstants.DELETE_CREDENTIAL)
    public ApiResponse<Void> deleteCredential(
            @PathVariable String projectCode,
            @PathVariable Long id) {
        permissionEvaluator.require("PROJECT_MANAGEMENT:DELETE");
        credentialService.deleteCredential(projectCode, id);
        return ApiResponse.success("Credential deleted", null);
    }

    @PostMapping("/{id}/reveal")
    @Operation(summary = ProjectManagementConstants.REVEAL_PASSWORD)
    public ApiResponse<Map<String, String>> revealPassword(
            @PathVariable String projectCode,
            @PathVariable Long id) {
        permissionEvaluator.require("PROJECT_MANAGEMENT:CREDENTIAL_REVEAL");
        String password = credentialService.revealPassword(projectCode, id);
        return ApiResponse.success("Password revealed (audit-logged)",
                Map.of("password", password));
    }
}
