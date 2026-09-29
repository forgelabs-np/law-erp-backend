package com.lawfirm.erp.common.exception;

/**
 * Object storage could not be reached or refused an operation.
 *
 * <p>Separate from {@link BusinessRuleException} on purpose: "the firm is out of space" is the
 * caller's problem, while "MinIO is down" is not, and the two must not share an HTTP status.
 */
public class StorageOperationException extends RuntimeException {

    public StorageOperationException(String message) {
        super(message);
    }

    public StorageOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
