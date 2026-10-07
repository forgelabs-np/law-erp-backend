package com.lawfirm.erp.modules.audit.annotation;

import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {

    AuditAction action();

    AuditEntity entity();

    String entityId() default "#result != null ? #result.id : null";

    String summary();

    boolean skipIfNullResult() default false;
}