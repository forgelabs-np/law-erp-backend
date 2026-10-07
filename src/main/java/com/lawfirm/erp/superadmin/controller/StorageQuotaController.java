package com.lawfirm.erp.superadmin.controller;

import com.lawfirm.erp.common.constant.DocumentConstants;
import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.common.storage.SetStorageQuotaRequest;
import com.lawfirm.erp.common.storage.StorageQuotaService;
import com.lawfirm.erp.common.storage.StorageUsageView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/super-admin/firms")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin - Firm Storage", description = "Per-firm document storage allocation")
public class StorageQuotaController {

    private final StorageQuotaService storageQuotaService;
    private final ResponseHandler responseHandler;

    @PutMapping("/{firmId}/storage-quota")
    @Operation(summary = DocumentConstants.SET_FIRM_QUOTA_SUMMARY,
            description = DocumentConstants.SET_FIRM_QUOTA_DESCRIPTION)
    public ResponseEntity<ApiResponse<StorageUsageView>> setQuota(
            @PathVariable UUID firmId,
            @Valid @RequestBody ApiRequest<SetStorageQuotaRequest> request) {
        return responseHandler.ok(
                storageQuotaService.setQuota(firmId, request.getData().getQuotaBytes()),
                "Storage allocation updated successfully");
    }

    @GetMapping("/{firmId}/storage-usage")
    @Operation(summary = DocumentConstants.GET_FIRM_QUOTA_SUMMARY)
    public ResponseEntity<ApiResponse<StorageUsageView>> usage(@PathVariable UUID firmId) {
        return responseHandler.ok(storageQuotaService.usage(firmId),
                "Storage usage fetched successfully");
    }
}
