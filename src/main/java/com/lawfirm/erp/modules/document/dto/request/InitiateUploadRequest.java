package com.lawfirm.erp.modules.document.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Asks for an upload ticket. Supply {@code matterNumber} (a case document) or
 * {@code projectCode} (a project document) — exactly one.
 */
@Getter
@Setter
public class InitiateUploadRequest {

    /** The case this document belongs to. */
    private String matterNumber;

    /** The project this document belongs to. */
    private String projectCode;

    /** Optional: tag the document with one specific court instance of the matter. */
    private String courtCaseRef;

    @NotBlank(message = "Filename is required")
    private String filename;

    @NotBlank(message = "Content type is required")
    private String contentType;

    @NotNull(message = "File size is required")
    @Positive(message = "File size must be greater than zero")
    private Long sizeBytes;
}
