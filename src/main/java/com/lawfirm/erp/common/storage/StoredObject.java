package com.lawfirm.erp.common.storage;

/** What storage knows about one object. {@code exists=false} means it is not there. */
public record StoredObject(boolean exists, long size, String etag, String contentType) {

    public static StoredObject missing() {
        return new StoredObject(false, 0L, null, null);
    }
}
