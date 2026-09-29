package com.lawfirm.erp.modules.document.service;

import com.lawfirm.erp.modules.document.dto.response.DocumentResponse;
import com.lawfirm.erp.modules.document.entity.Document;
import org.springframework.stereotype.Component;

/** Entity → response. Owner references are resolved by the service and passed in. */
@Component
public class DocumentMapper {

    public DocumentResponse toResponse(Document document, String matterNumber, String projectCode) {
        return DocumentResponse.builder()
                .id(document.getId())
                .uuid(document.getUuid())
                .fileName(document.getOriginalFilename())
                .contentType(document.getContentType())
                .extension(document.getExtension())
                .sizeBytes(document.getSizeBytes())
                .status(document.getStatus())
                .visibility(document.getVisibility())
                .matterNumber(matterNumber)
                .projectCode(projectCode)
                .courtCaseId(document.getCourtCaseId())
                .uploadedByUserId(document.getUploadedByUserId())
                .createdAt(document.getCreatedAt())
                .archivedAt(document.getArchivedAt())
                .build();
    }
}
