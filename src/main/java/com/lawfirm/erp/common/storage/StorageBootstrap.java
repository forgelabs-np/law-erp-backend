package com.lawfirm.erp.common.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Verifies the bucket exists at startup.
 *
 * <p>An unreachable object store is logged rather than fatal: the application has plenty of
 * functionality that does not touch storage, and failing to boot because MinIO is down would
 * take all of it out. Requests that do need storage surface a 502 instead.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StorageBootstrap implements ApplicationRunner {

    private final StorageService storageService;

    @Override
    public void run(ApplicationArguments args) {
        if (!storageService.isConfigured()) {
            log.warn("Object storage is not configured (storage.minio.endpoint is empty) — "
                    + "document upload and download will fail until it is set");
            return;
        }
        try {
            storageService.ensureBucket();
            log.info("Object storage is ready");
        } catch (Exception e) {
            log.warn("Object storage is configured but not reachable at startup: {}", e.getMessage());
        }
    }
}
