package com.lawfirm.erp.common.storage;

import com.lawfirm.erp.common.exception.StorageOperationException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
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
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService implements StorageService {

    private final ObjectProvider<MinioClient> minioClientProvider;
    private final StorageProperties properties;

    @Override
    public StoredObject put(String key, InputStream data, long sizeBytes, String contentType) {
        try {
            ObjectWriteResponse response = client().putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .stream(data, sizeBytes, -1)
                    .contentType(contentType)
                    .build());
            return new StoredObject(true, sizeBytes, response.etag(), contentType);
        } catch (Exception e) {
            throw new StorageOperationException("Could not store the uploaded file", e);
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
    public String presignFirmLogo(String objectKey) {
        // No response-content-disposition: a logo is displayed inline in an <img>, not downloaded.
        return presignDownload(objectKey, null, Duration.ofMinutes(15));
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
            throw new StorageOperationException("Could not ensure object storage bucket", e);
        }
    }

    @Override
    public boolean isConfigured() {
        try {
            return client().bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build());
        } catch (Exception e) {
            log.debug("Object storage not configured: {}", e.getMessage());
            return false;
        }
    }

    private MinioClient client() {
        MinioClient client = minioClientProvider.getIfAvailable();
        if (client == null) {
            throw new StorageOperationException("MinIO client is not available");
        }
        return client;
    }

    private String headerSafe(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        return filename.replace("\"", "%22");
    }
}
