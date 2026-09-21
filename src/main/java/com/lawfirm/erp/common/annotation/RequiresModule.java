package com.lawfirm.erp.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller class or method as requiring a specific module to be enabled
 * for the current firm. If the module is disabled, the request is rejected with 403.
 *
 * <p>Usage: {@code @RequiresModule("CASE_MANAGEMENT")} on a controller class applies
 * to all its endpoints. Override at method level with a different module code if needed.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresModule {
    /** Module code that must be enabled (e.g. "CASE_MANAGEMENT", "BILLING"). */
    String value();
}
