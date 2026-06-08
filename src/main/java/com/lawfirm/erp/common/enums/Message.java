package com.lawfirm.erp.common.enums;


public enum Message {
    SUCCESS("success"),
    CREATE_SUCCESS("create.success"),
    UPDATE_SUCCESS("update.success"),
    DELETE_SUCCESS("delete.success"),
    FETCHED_SUCCESS("fetched.success"),
    TOGGLE_SUCCESS("toggle.success"),

    ALREADY_EXISTS("already.exists"),
    NOT_FOUND("not.found"),
    REQUIRED_DATA("required.data"),
    INVALID_DATA("invalid.data"),
    EXPIRED("expired"),
    INTERNAL_SERVER_ERROR("internal.server.error");

    private final String code;

    Message(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}