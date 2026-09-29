package com.lawfirm.erp.modules.document.controller;

import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.constant.DocumentConstants;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.modules.document.dto.response.DocumentResponse;
import com.lawfirm.erp.modules.document.dto.response.DownloadUrlResponse;
import com.lawfirm.erp.modules.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client portal — documents on the client's own cases and projects.
 *
 * <p>Read-only by construction: there is no upload or delete endpoint here, and the service
 * query ignores any visibility or status the caller asks for, always returning shared, active
 * documents on matters and projects the caller owns.
 */
@RestController
@RequestMapping("/api/v1/client/documents")
@RequiredArgsConstructor
@Tag(name = "Client Portal - Documents", description = "Documents shared with the signed-in client")
public class ClientDocumentController {

    private static final String VIEW = "DOCUMENT_MANAGEMENT:VIEW";

    private final DocumentService documentService;
    private final PermissionEvaluator permissionEvaluator;

    @GetMapping
    @Operation(summary = DocumentConstants.LIST_MY_DOCUMENTS_SUMMARY,
            description = "Only documents shared with you, on your own cases and projects")
    public ApiResponse<PagedResponse<DocumentResponse>> listMyDocuments(
            @RequestParam(required = false) String matterNumber,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        permissionEvaluator.require(VIEW);
        return ApiResponse.success(documentService.listForClient(
                matterNumber, projectCode, search, page, size));
    }

    @GetMapping("/{documentId}/download-url")
    @Operation(summary = DocumentConstants.DOWNLOAD_SUMMARY,
            description = DocumentConstants.DOWNLOAD_DESCRIPTION)
    public ApiResponse<DownloadUrlResponse> download(@PathVariable Long documentId) {
        permissionEvaluator.require(VIEW);
        return ApiResponse.success(documentService.downloadUrl(documentId));
    }
}
