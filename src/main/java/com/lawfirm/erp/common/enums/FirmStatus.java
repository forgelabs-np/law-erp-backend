package com.lawfirm.erp.common.enums;

public enum FirmStatus {
    ACTIVE, SUSPENDED, TRIAL,
    /** Reserved/legacy — the scheduler auto-suspends on trial expiry rather than setting this. */
    EXPIRED
}