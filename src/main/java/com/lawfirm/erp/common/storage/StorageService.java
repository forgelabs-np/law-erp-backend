package com.lawfirm.erp.common.storage;

import java.time.Duration;

/**
 * Generic object-storage operations.
 *
 * <p>Deliberately knows nothing about documents, cases or projects: any feature that needs
 * to move bytes (invoice PDFs, scraper exports, report generation) can depend on this
 * interface as-is. Implementations are swappable, and because this is an interface the
 * tests can supply an in-memory double and never need a running object store.
 */
public interface StorageService {

    /**
     * A presigned POST policy for one key. Storage itself enforces the maximum size and the
     * exact {@code contentType} — an oversized or mislabelled upload is refused before it is
     * stored, which a presigned PUT could not do.
     */
    UploadTicket presignUpload(String key, String contentType, long maxBytes, Duration ttl);

    /** A presigned GET for one key, forcing a browser download instead of inline rendering. */
    String presignDownload(String key, String downloadFilename, Duration ttl);

    StoredObject stat(String key);

    /** First {@code maxBytes} of an object, for file-signature checks without a full download. */
    byte[] readHead(String key, int maxBytes);

    void delete(String key);

    /** Creates the bucket when missing. Safe to call repeatedly. */
    void ensureBucket();

    boolean isConfigured();
}
