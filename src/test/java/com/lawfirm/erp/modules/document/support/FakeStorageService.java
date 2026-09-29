package com.lawfirm.erp.modules.document.support;

import com.lawfirm.erp.common.storage.StorageService;
import com.lawfirm.erp.common.storage.StoredObject;
import com.lawfirm.erp.common.storage.UploadTicket;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for object storage, so no test needs a running MinIO.
 *
 * <p>{@link #put} simulates the browser completing the presigned POST: the test reads the
 * generated key out of the upload ticket and stores the exact bytes it wants confirm to see.
 * That keeps the confirm-time size and signature checks genuinely under test rather than
 * stubbed away.
 */
public class FakeStorageService implements StorageService {

    /** key → object bytes. */
    public final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    /** Keys passed to {@link #delete}, in order — proves compensation actually ran. */
    public final List<String> deletedKeys = new ArrayList<>();

    /** Set false to simulate storage never being configured. */
    public boolean configured = true;

    public void put(String key, byte[] content) {
        objects.put(key, content);
    }

    public boolean has(String key) {
        return objects.containsKey(key);
    }

    @Override
    public UploadTicket presignUpload(String key, String contentType, long maxBytes, Duration ttl) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("key", key);
        fields.put("Content-Type", contentType);
        fields.put("policy", "fake-" + maxBytes);
        fields.put("x-amz-signature", "fake-signature");
        return new UploadTicket(key, "http://fake-storage/" + key, fields, Instant.now().plus(ttl));
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
