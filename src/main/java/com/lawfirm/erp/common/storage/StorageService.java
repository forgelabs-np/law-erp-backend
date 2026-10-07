package com.lawfirm.erp.common.storage;

import java.io.InputStream;
import java.time.Duration;

public interface StorageService {

    StoredObject put(String key, InputStream data, long sizeBytes, String contentType);

    String presignDownload(String key, String downloadFilename, Duration ttl);

    StoredObject stat(String key);

    byte[] readHead(String key, int maxBytes);

    void delete(String key);

    void ensureBucket();

    boolean isConfigured();
}
