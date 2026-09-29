package com.lawfirm.erp.modules.document.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * How to perform the upload: POST {@code fields} plus the file to {@code uploadUrl}, then call
 * confirm. Every field must be sent unchanged — the policy signature covers them.
 */
public record UploadTicketResponse(
        Long documentId,
        String fileName,
        String uploadUrl,
        Map<String, String> fields,
        Instant expiresAt
) {
}
