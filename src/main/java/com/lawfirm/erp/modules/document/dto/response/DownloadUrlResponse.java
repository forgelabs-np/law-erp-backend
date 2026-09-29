package com.lawfirm.erp.modules.document.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * A short-lived download link. The URL is a bearer credential — it is never logged, and it
 * expires.
 */
public record DownloadUrlResponse(UUID documentUuid, String downloadUrl, String fileName, Instant expiresAt) {
}
