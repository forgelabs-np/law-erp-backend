package com.lawfirm.erp.modules.document.service;

import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.storage.StorageUsageView;
import com.lawfirm.erp.modules.document.dto.response.DocumentResponse;
import com.lawfirm.erp.modules.document.dto.response.DownloadUrlResponse;
import com.lawfirm.erp.modules.document.enums.DocumentStatus;
import com.lawfirm.erp.modules.document.enums.DocumentVisibility;

import java.io.InputStream;

public interface DocumentService {

    DocumentResponse upload(String matterNumber, String projectCode, String courtCaseRef,
                            String originalFilename, String contentType, long sizeBytes,
                            InputStream content);

    PagedResponse<DocumentResponse> listLibrary(DocumentStatus status, DocumentVisibility visibility,
                                                String search, int page, int size);

    PagedResponse<DocumentResponse> listForMatter(String matterNumber, DocumentStatus status,
                                                 DocumentVisibility visibility, String search,
                                                 int page, int size);

    PagedResponse<DocumentResponse> listForProject(String projectCode, DocumentStatus status,
                                                  DocumentVisibility visibility, String search,
                                                  int page, int size);

    PagedResponse<DocumentResponse> listForClient(String matterNumber, String projectCode,
                                                  String search, int page, int size);

    DownloadUrlResponse downloadUrl(Long documentId);

    DocumentResponse updateVisibility(Long documentId, DocumentVisibility visibility);

    DocumentResponse archive(Long documentId);

    StorageUsageView storageUsage();
}
