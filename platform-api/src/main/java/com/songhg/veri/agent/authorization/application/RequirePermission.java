package com.songhg.veri.agent.authorization.application;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    String value();

    /**
     * Optional SpEL expression that resolves a ResourceScope, scope id, or collection of scopes.
     */
    String scope() default "";

    /**
     * Scope type used when {@link #scope()} returns plain ids rather than ResourceScope instances.
     */
    String scopeType() default "PROJECT";
}
