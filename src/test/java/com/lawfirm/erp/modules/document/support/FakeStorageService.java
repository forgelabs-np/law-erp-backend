package com.lawfirm.erp.modules.document.support;

import com.lawfirm.erp.common.exception.StorageOperationException;
import com.lawfirm.erp.common.storage.StorageService;
import com.lawfirm.erp.common.storage.StoredObject;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for object storage, so no test needs a running MinIO.
 *
 * <p>{@link #put} consumes the caller's stream exactly as storage would, so the upload-time size
 * and signature checks stay genuinely under test. {@link #seed} pre-fills an object without going
 * through the upload path.
 */
public class FakeStorageService implements StorageService {

    /** key → object bytes. */
    public final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    /** Keys passed to {@link #delete}, in order — proves compensation actually ran. */
    public final List<String> deletedKeys = new ArrayList<>();

    /** Set false to simulate storage never being configured. */
    public boolean configured = true;

    /** Set true to make {@link #put} fail, as an unreachable store would. */
    public boolean failingWrites = false;

    /** Pre-fills an object without exercising the upload path. */
    public void seed(String key, byte[] content) {
        objects.put(key, content);
    }

    public boolean has(String key) {
        return objects.containsKey(key);
    }

    @Override
    public StoredObject put(String key, InputStream data, long sizeBytes, String contentType) {
        if (failingWrites) {
            throw new StorageOperationException("Could not store the uploaded file");
        }
        byte[] bytes;
        try {
            bytes = data.readAllBytes();
        } catch (IOException e) {
            throw new StorageOperationException("Could not store the uploaded file", e);
        }
        objects.put(key, bytes);
        return new StoredObject(true, bytes.length, md5Hex(bytes), contentType);
    }

    @Override
    public String presignDownload(String key, String downloadFilename, Duration ttl) {
        return "http://fake-storage/download/" + key + "?ttl=" + ttl.toSeconds();
    }

    @Override
    public StoredObject stat(String key) {
        byte[] content = objects.get(key);
        if (content == null) {
            return StoredObject.missing();
        }
        // Mirrors S3/MinIO: a 32-character hex MD5 of the content.
        return new StoredObject(true, content.length, md5Hex(content), null);
    }

    private static String md5Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(data);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    @Override
    public byte[] readHead(String key, int maxBytes) {
        byte[] content = objects.get(key);
        if (content == null) {
            return new byte[0];
        }
        return Arrays.copyOf(content, Math.min(maxBytes, content.length));
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
        deletedKeys.add(key);
    }

    @Override
    public void ensureBucket() {
        // no bucket to create
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }
}
