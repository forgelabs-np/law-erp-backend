package com.lawfirm.erp.common.storage;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
@Slf4j
public class StorageConfig {

    /**
     * Only built when an endpoint is configured, so the app and the test suite start with no
     * object storage present. {@link MinioStorageService} resolves it lazily and reports a
     * clear error on use.
     */
    @Bean
    @ConditionalOnProperty(prefix = "storage.minio", name = "endpoint")
    public MinioClient minioClient(StorageProperties properties) {
        log.info("Object storage configured: endpoint={}, bucket={}",
                properties.getEndpoint(), properties.getBucket());
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .region(properties.getRegion())
                .build();
    }
}
