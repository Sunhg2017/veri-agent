package com.songhg.veri.agent.common.openapi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiVersion {
    String value() default "v1";

    ApiLifecycle lifecycle() default ApiLifecycle.STABLE;

    String since() default "2026-05";

    String sunset() default "";

    String replacement() default "";
}
