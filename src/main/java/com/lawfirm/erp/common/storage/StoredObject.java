package com.lawfirm.erp.common.storage;

public record StoredObject(boolean exists, long size, String etag, String contentType) {

    public static StoredObject missing() {
        return new StoredObject(false, 0L, null, null);
    }
}
