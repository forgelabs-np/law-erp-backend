package com.lawfirm.erp.common.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every object-storage knob, declared once. Nothing outside
 * {@code com.lawfirm.erp.common.storage} reads these values, and no feature module talks
 * to MinIO directly — they call {@link StorageService}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "storage.minio")
public class StorageProperties {

    /** Full URL including scheme, e.g. {@code http://localhost:9000}. Must be reachable from the browser. */
    private String endpoint;

    private String accessKey;

    private String secretKey;

    private String bucket = "tarikh-documents";

    private String region = "us-east-1";

    /** Presigned POST validity for uploads. */
    private int uploadExpirySeconds = 1800;

    /** Presigned GET validity for downloads. */
    private int downloadExpirySeconds = 900;

    private long maxFileSizeBytes = 50L * 1024 * 1024;

    /** Allocation given to a firm the first time it stores anything. 0 = unlimited. */
    private long defaultQuotaBytes = 5L * 1024 * 1024 * 1024;

    public boolean isConfigured() {
        return endpoint != null && !endpoint.isBlank();
    }
}
