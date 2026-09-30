package com.lawfirm.erp.encryption;
import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Decrypt {
    boolean enabled() default true;
}
