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

    /** The identifier the audit trail records for this document. */
    private UUID uuid;

    private String fileName;
    private String contentType;
    private String extension;
    private long sizeBytes;
    private DocumentStatus status;
    private DocumentVisibility visibility;

    /**
     * Short-lived presigned download link, present only while the document is {@code ACTIVE}.
     *
     * <p>It is a bearer credential (like {@code download-url}'s) and it expires, so it must not
     * be logged, persisted or cached past its lifetime. {@code PENDING_UPLOAD} rows have none
     * (the object may never have arrived) and {@code ARCHIVED} rows deliberately have none.
     * Fetch {@code GET /documents/{id}/download-url} for a fresh link at download time.
     */
    private String documentUrl;

    /** Which case this belongs to — null for a project document. */
    private String matterNumber;

    /** Which project this belongs to — null for a case document. */
    private String projectCode;

    private UUID courtCaseId;
    private UUID uploadedByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime archivedAt;
}
