package com.lawfirm.erp.entity.base;
import java.io.Serializable;
import java.util.UUID;

public interface BaseEntity {
    Serializable getId();
    UUID getUuid();
}

