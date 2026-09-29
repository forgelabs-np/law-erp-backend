package com.lawfirm.erp.common.storage;

import com.lawfirm.erp.common.exception.StorageOperationException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PostPolicy;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The only class in the codebase that imports {@code io.minio}.
 *
 * <p>The client is resolved lazily through {@link ObjectProvider} so the application (and the
 * test suite) boots with no storage configured; any attempt to actually use storage then
 * fails with a clear {@link StorageOperationException} instead of a startup error.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService implements StorageService {

    private static final String KEY_FIELD = "key";
    private static final String CONTENT_TYPE_FIELD = "Content-Type";

    private final ObjectProvider<MinioClient> minioClientProvider;
    private final StorageProperties properties;

    @Override
    public UploadTicket presignUpload(String key, String contentType, long maxBytes, Duration ttl) {
        MinioClient client = client();
        try {
            PostPolicy policy = new PostPolicy(properties.getBucket(),
                    ZonedDateTime.now().plus(ttl));
            policy.addContentLengthRangeCondition(1L, maxBytes);
            // Exact key and exact content type — both are signed, so neither can be tampered with.
            policy.addEqualsCondition(KEY_FIELD, key);
            policy.addEqualsCondition(CONTENT_TYPE_FIELD, contentType);

            Map<String, String> fields = new LinkedHashMap<>(client.getPresignedPostFormData(policy));
            // The SDK returns only the signature fields. These two are policy conditions the
            // client must echo back, so they travel with the ticket.
            fields.put(KEY_FIELD, key);
            fields.put(CONTENT_TYPE_FIELD, contentType);

            return new UploadTicket(key, postUrl(), fields, Instant.now().plus(ttl));
        } catch (Exception e) {
            throw new StorageOperationException("Could not create an upload ticket", e);
        }
    }

    @Override
    public String presignDownload(String key, String downloadFilename, Duration ttl) {
        MinioClient client = client();
        try {
            Map<String, String> query = new LinkedHashMap<>();
            if (downloadFilename != null && !downloadFilename.isBlank()) {
                query.put("response-content-disposition",
                        "attachment; filename=\"" + headerSafe(downloadFilename) + "\"");
            }
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getBucket())
                    .object(key)
                    .expiry((int) ttl.toSeconds())
                    .extraQueryParams(query)
                    .build());
        } catch (Exception e) {
            throw new StorageOperationException("Could not create a download link", e);
        }
    }

    @Override
    public StoredObject stat(String key) {
        try {
            StatObjectResponse stat = client().statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .build());
            return new StoredObject(true, stat.size(), stat.etag(), stat.contentType());
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return StoredObject.missing();
            }
            throw new StorageOperationException("Could not read object metadata", e);
        } catch (Exception e) {
            throw new StorageOperationException("Could not read object metadata", e);
        }
    }

    @Override
    public byte[] readHead(String key, int maxBytes) {
        try (InputStream stream = client().getObject(GetObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(key)
                .offset(0L)
                .length((long) maxBytes)
                .build())) {
            return stream.readNBytes(maxBytes);
        } catch (Exception e) {
            throw new StorageOperationException("Could not read the uploaded object", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .build());
        } catch (Exception e) {
            throw new StorageOperationException("Could not remove the stored object", e);
        }
    }

    @Override
    public void ensureBucket() {
        MinioClient client = client();
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
                log.info("Created object storage bucket '{}'", properties.getBucket());
            }
        } catch (Exception e) {
            throw new StorageOperationException("Could not verify the storage bucket", e);
        }
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    private MinioClient client() {
        MinioClient client = minioClientProvider.getIfAvailable();
        if (client == null) {
            throw new StorageOperationException(
                    "Object storage is not configured (storage.minio.endpoint is empty)");
        }
        return client;
    }

    /** POST target for form uploads: {@code {endpoint}/{bucket}}. */
    private String postUrl() {
        return properties.getEndpoint().replaceAll("/+$", "") + "/" + properties.getBucket();
    }

    /** Keeps a filename out of the Content-Disposition header structure (CRLF/quote injection). */
    private String headerSafe(String value) {
        return value.replaceAll("[\\r\\n\"]", "_");
    }
}
