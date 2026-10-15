package com.lawfirm.erp.modules.document.dto.response;

import com.lawfirm.erp.modules.document.enums.DocumentStatus;
import com.lawfirm.erp.modules.document.enums.DocumentVisibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentResponse {

    private Long id;

    private UUID uuid;

    private String fileName;
    private String contentType;
    private String extension;
    private long sizeBytes;
    private DocumentStatus status;
    private DocumentVisibility visibility;

    private String documentUrl;

    private String matterNumber;

    private String projectCode;

    private UUID courtCaseId;
    private UUID uploadedByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime archivedAt;
}
