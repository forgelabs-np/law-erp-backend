package com.lawfirm.erp.common.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "storage.minio")
public class StorageProperties {

    private String endpoint;

    private String accessKey;

    private String secretKey;

    private String bucket = "tarikh-documents";

    private String region = "us-east-1";

    private int uploadExpirySeconds = 1800;

    private int downloadExpirySeconds = 900;

    private long maxFileSizeBytes = 50L * 1024 * 1024;

    private long defaultQuotaBytes = 5L * 1024 * 1024 * 1024;

    public boolean isConfigured() {
        return endpoint != null && !endpoint.isBlank();
    }
}
