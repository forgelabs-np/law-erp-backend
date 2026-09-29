package com.lawfirm.erp.modules.document.service;

import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.storage.StorageUsageView;
import com.lawfirm.erp.modules.document.dto.request.ConfirmUploadRequest;
import com.lawfirm.erp.modules.document.dto.request.InitiateUploadRequest;
import com.lawfirm.erp.modules.document.dto.response.DocumentResponse;
import com.lawfirm.erp.modules.document.dto.response.DownloadUrlResponse;
import com.lawfirm.erp.modules.document.dto.response.UploadTicketResponse;
import com.lawfirm.erp.modules.document.enums.DocumentStatus;
import com.lawfirm.erp.modules.document.enums.DocumentVisibility;

public interface DocumentService {

    UploadTicketResponse initiateUpload(InitiateUploadRequest request);

    DocumentResponse confirmUpload(Long documentId, ConfirmUploadRequest request);

    PagedResponse<DocumentResponse> listLibrary(DocumentStatus status, DocumentVisibility visibility,
                                                String search, int page, int size);

    PagedResponse<DocumentResponse> listForMatter(String matterNumber, DocumentStatus status,
                                                 DocumentVisibility visibility, String search,
                                                 int page, int size);

    PagedResponse<DocumentResponse> listForProject(String projectCode, DocumentStatus status,
                                                  DocumentVisibility visibility, String search,
                                                  int page, int size);

    /** Client portal: own case/project, shared and active only. */
    PagedResponse<DocumentResponse> listForClient(String matterNumber, String projectCode,
                                                  String search, int page, int size);

    DownloadUrlResponse downloadUrl(Long documentId);

    DocumentResponse updateVisibility(Long documentId, DocumentVisibility visibility);

    DocumentResponse archive(Long documentId);

    StorageUsageView storageUsage();
}
