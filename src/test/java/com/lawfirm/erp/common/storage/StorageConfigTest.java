package com.lawfirm.erp.common.storage;

import io.minio.MinioClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for the production boot path: {@code application-prod.yml} defaults
 * {@code storage.minio.endpoint} to an empty string when {@code MINIO_ENDPOINT} is not set.
 * {@code MinioClient} rejects an empty endpoint, so the bean must only be created for a
 * non-blank value — otherwise the whole application fails to start.
 */
class StorageConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StorageConfig.class);

    @Test
    @DisplayName("no endpoint at all: the app boots and no MinioClient is created")
    void missingEndpointLeavesTheAppBootable() {
        runner.run(context -> {
            assertNull(context.getStartupFailure(), "the context must start with no storage configured");
            assertNull(context.getBeanProvider(MinioClient.class).getIfAvailable(),
                    "no client should exist without an endpoint");
        });
    }

    @Test
    @DisplayName("empty endpoint (the prod yml default): the app boots and no MinioClient is created")
    void emptyEndpointLeavesTheAppBootable() {
        runner.withPropertyValues("storage.minio.endpoint=").run(context -> {
            assertNull(context.getStartupFailure(), "an empty endpoint must not fail startup");
            assertNull(context.getBeanProvider(MinioClient.class).getIfAvailable(),
                    "an empty endpoint must not create a client");
        });
    }

    @Test
    @DisplayName("configured endpoint: the MinioClient is created")
    void configuredEndpointCreatesTheClient() {
        runner.withPropertyValues(
                        "storage.minio.endpoint=http://localhost:9000",
                        "storage.minio.access-key=test-access",
                        "storage.minio.secret-key=test-secret")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBeanProvider(MinioClient.class).getIfAvailable(),
                            "a configured endpoint should create the client");
                });
    }

    @Test
    @DisplayName("storage is reported unconfigured until an endpoint is set")
    void isConfiguredReflectsTheEndpoint() {
        StorageProperties properties = new StorageProperties();
        assertFalse(properties.isConfigured(), "a null endpoint is not configured");
        properties.setEndpoint("");
        assertFalse(properties.isConfigured(), "a blank endpoint is not configured");
        properties.setEndpoint("https://storage.example.com");
        assertTrue(properties.isConfigured());
    }
}
