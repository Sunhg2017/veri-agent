package com.songhg.veri.agent.authorization.application;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RequirePermissionAspect {

    private final AuthorizationService authorizationService;

    public RequirePermissionAspect(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Before("@annotation(requirePermission)")
    public void requirePermission(RequirePermission requirePermission) {
        authorizationService.requireCurrent(requirePermission.value());
    }
}
