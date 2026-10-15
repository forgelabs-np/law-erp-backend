package com.lawfirm.erp.modules.document.controller;

import com.lawfirm.erp.auth.security.PermissionEvaluator;
import com.lawfirm.erp.common.constant.DocumentConstants;
import com.lawfirm.erp.common.dto.ApiRequest;
import com.lawfirm.erp.common.dto.ApiResponse;
import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.common.storage.StorageUsageView;
import com.lawfirm.erp.modules.document.dto.request.UpdateVisibilityRequest;
import com.lawfirm.erp.modules.document.dto.response.DocumentResponse;
import com.lawfirm.erp.modules.document.dto.response.DownloadUrlResponse;
import com.lawfirm.erp.modules.document.enums.DocumentStatus;
import com.lawfirm.erp.modules.document.enums.DocumentVisibility;
import com.lawfirm.erp.modules.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/firm")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Case and project documents stored in object storage")
public class DocumentController {

    private static final String UPLOAD = "DOCUMENT_MANAGEMENT:UPLOAD";
    private static final String VIEW = "DOCUMENT_MANAGEMENT:VIEW";
    private static final String EDIT = "DOCUMENT_MANAGEMENT:EDIT";
    private static final String SHARE = "DOCUMENT_MANAGEMENT:SHARE";

    private final DocumentService documentService;
    private final PermissionEvaluator permissionEvaluator;
    private final ResponseHandler responseHandler;

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = DocumentConstants.UPLOAD_SUMMARY,
            description = DocumentConstants.UPLOAD_DESCRIPTION)
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String matterNumber,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String courtCaseRef) throws IOException {
        permissionEvaluator.require(UPLOAD);
        return responseHandler.ok(documentService.upload(matterNumber, projectCode, courtCaseRef,
                        file.getOriginalFilename(), file.getContentType(), file.getSize(),
                        file.getInputStream()),
                "Document uploaded successfully");
    }

    @GetMapping("/documents")
    @Operation(summary = DocumentConstants.LIST_LIBRARY_SUMMARY,
            description = DocumentConstants.LIST_LIBRARY_DESCRIPTION)
    public ResponseEntity<ApiResponse<PagedResponse<DocumentResponse>>> listLibrary(
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) DocumentVisibility visibility,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        permissionEvaluator.require(VIEW);
        return responseHandler.ok(documentService.listLibrary(status, visibility, search, page, size),
                "Documents fetched successfully");
    }

    @GetMapping("/matters/{matterNumber}/documents")
    @Operation(summary = DocumentConstants.LIST_CASE_DOCUMENTS_SUMMARY)
    public ResponseEntity<ApiResponse<PagedResponse<DocumentResponse>>> listCaseDocuments(
            @PathVariable String matterNumber,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) DocumentVisibility visibility,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        permissionEvaluator.require(VIEW);
        return responseHandler.ok(
                documentService.listForMatter(matterNumber, status, visibility, search, page, size),
                "Documents fetched successfully");
    }

    @GetMapping("/projects/{projectCode}/documents")
    @Operation(summary = DocumentConstants.LIST_PROJECT_DOCUMENTS_SUMMARY)
    public ResponseEntity<ApiResponse<PagedResponse<DocumentResponse>>> listProjectDocuments(
            @PathVariable String projectCode,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) DocumentVisibility visibility,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        permissionEvaluator.require(VIEW);
        return responseHandler.ok(
                documentService.listForProject(projectCode, status, visibility, search, page, size),
                "Documents fetched successfully");
    }

    @GetMapping("/documents/{documentId}/download-url")
    @Operation(summary = DocumentConstants.DOWNLOAD_SUMMARY,
            description = DocumentConstants.DOWNLOAD_DESCRIPTION)
    public ResponseEntity<ApiResponse<DownloadUrlResponse>> download(
            @PathVariable Long documentId) {
        permissionEvaluator.require(VIEW);
        return responseHandler.ok(documentService.downloadUrl(documentId),
                "Download link generated successfully");
    }

    @PatchMapping("/documents/{documentId}/visibility")
    @Operation(summary = DocumentConstants.SET_VISIBILITY_SUMMARY,
            description = DocumentConstants.SET_VISIBILITY_DESCRIPTION)
    public ResponseEntity<ApiResponse<DocumentResponse>> updateVisibility(
            @PathVariable Long documentId,
            @Valid @RequestBody ApiRequest<UpdateVisibilityRequest> request) {
        permissionEvaluator.require(SHARE);
        return responseHandler.ok(
                documentService.updateVisibility(documentId, request.getData().getVisibility()),
                "Document visibility updated successfully");
    }

    @DeleteMapping("/documents/{documentId}")
    @Operation(summary = DocumentConstants.ARCHIVE_SUMMARY,
            description = DocumentConstants.ARCHIVE_DESCRIPTION)
    public ResponseEntity<ApiResponse<DocumentResponse>> archive(@PathVariable Long documentId) {
        permissionEvaluator.require(EDIT);
        return responseHandler.ok(documentService.archive(documentId),
                "Document archived successfully");
    }

    @GetMapping("/documents/storage-usage")
    @Operation(summary = DocumentConstants.STORAGE_USAGE_SUMMARY)
    public ResponseEntity<ApiResponse<StorageUsageView>> storageUsage() {
        permissionEvaluator.require(VIEW);
        return responseHandler.ok(documentService.storageUsage(), "Storage usage fetched successfully");
    }
}
