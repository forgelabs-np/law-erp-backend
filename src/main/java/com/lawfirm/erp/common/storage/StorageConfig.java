package com.lawfirm.erp.common.storage;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
@Slf4j
public class StorageConfig {

    // The endpoint is present-but-empty in application-prod.yml when MINIO_ENDPOINT is not set, and
    // MinioClient rejects an empty endpoint — so a plain @ConditionalOnProperty (which matches on the
    // key existing, empty or not) still built the bean and killed startup. Require a non-blank value.
    @Bean
    @ConditionalOnExpression("'${storage.minio.endpoint:}'.trim().length() > 0")
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
