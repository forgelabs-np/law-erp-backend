package com.lawfirm.erp.common.storage;

import java.io.InputStream;
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
     * Stores one object and returns what storage recorded for it (size + etag).
     *
     * <p>Bytes are written by the application rather than by the browser, so the caller
     * already holds the file: it is responsible for the size and content-type checks. Storage
     * is only the durable target here.
     */
    StoredObject put(String key, InputStream data, long sizeBytes, String contentType);

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
