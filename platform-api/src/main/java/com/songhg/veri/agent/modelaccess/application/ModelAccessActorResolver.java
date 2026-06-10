package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.authorization.application.PermissionCodes;
import com.songhg.veri.agent.common.error.PlatformAccessDeniedException;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import org.springframework.stereotype.Service;

/**
 * Resolves model-access actors from the common security context for API and application use cases.
 */
@Service
public class ModelAccessActorResolver {

    private final AuthorizationService authorizationService;

    public ModelAccessActorResolver(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public AuthUserPrincipal currentUserPrincipal() {
        return authorizationService.currentUserPrincipal();
    }

    public ServicePrincipal invocationPrincipal() {
        ServicePrincipal servicePrincipal = authorizationService.currentServicePrincipal();
        if (servicePrincipal != null) {
            return servicePrincipal;
        }
        AuthUserPrincipal principal = authorizationService.currentUserPrincipal();
        if (principal != null) {
            return new ServicePrincipal("model-access-console", principal.userId().toString(), principal.roles());
        }
        throw new PlatformAccessDeniedException(PermissionCodes.MODEL_ACCESS_MANAGE);
    }
}
