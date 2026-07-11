package com.lawfirm.erp.modules.audit.annotation;

import com.lawfirm.erp.common.enums.AuditAction;
import com.lawfirm.erp.common.enums.AuditEntity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark any service method to automatically log an audit entry.
 *
 * Usage:
 *   @Audit(action = AuditAction.USER_CREATED, entity = AuditEntity.USER)
 *   public EmployeeResponse createEmployee(CreateEmployeeRequest request) { ... }
 *
 * The action and entity are fixed at compile time.
 * entityId is resolved via SpEL at runtime:
 *   - "#result.id"        → use return value's id field
 *   - "#request.userId"   → use parameter's userId field
 *   - "#userId"           → use method parameter named userId
 *
 * Summary is built using SpEL too:
 *   summary = "Created employee: " + #request.username
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {

    AuditAction action();

    AuditEntity entity();

    /**
     * SpEL expression to extract the entity ID.
     * Examples:
     *   - "#result.id"                → use returned object's id
     *   - "#request.userId"           → use parameter's userId field
     *   - "#employeeId"               → use method parameter directly
     *   - "#result != null ? #result.id : null"
     */
    String entityId() default "#result != null ? #result.id : null";

    /**
     * SpEL expression to build the summary message.
     * Examples:
     *   - "'Created employee: ' + #request.username"
     *   - "'Updated role for: ' + #user.username + ' from ' + #oldRole + ' to ' + #newRole"
     *   - "'Deleted case: ' + #caseId"
     */
    String summary();

    /**
     * Optional: skip logging if the method returns null.
     * Default: false (always log, even if result is null).
     * Useful for delete operations where result is null.
     */
    boolean skipIfNullResult() default false;
}