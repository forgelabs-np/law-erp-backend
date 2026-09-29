package com.lawfirm.erp.common.storage;

import java.time.Instant;
import java.util.Map;

/**
 * Everything the client needs to POST one object straight to storage.
 *
 * <p>The {@code fields} map is the complete form body (excluding the file part) and must be
 * sent verbatim — the policy signature covers them, so altering any value is rejected by
 * storage rather than silently accepted.
 */
public record UploadTicket(String key, String url, Map<String, String> fields, Instant expiresAt) {
}
