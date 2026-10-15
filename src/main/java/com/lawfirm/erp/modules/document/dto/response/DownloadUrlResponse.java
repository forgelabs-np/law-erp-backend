package com.lawfirm.erp.modules.document.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DownloadUrlResponse(UUID documentUuid, String downloadUrl, String fileName, Instant expiresAt) {
}
