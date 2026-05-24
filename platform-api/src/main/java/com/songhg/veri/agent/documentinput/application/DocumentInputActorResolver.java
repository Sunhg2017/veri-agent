package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
final class DocumentInputActorResolver {

    private final AuthorizationService authorizationService;

    DocumentInputActorResolver(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    /**
     * Resolves the effective actor for audit and model invocation records.
     */
    String currentActor() {
        ServicePrincipal servicePrincipal = authorizationService.currentServicePrincipal();
        if (servicePrincipal != null && StringUtils.hasText(servicePrincipal.delegatedUserId())) {
            return servicePrincipal.delegatedUserId();
        }
        AuthUserPrincipal userPrincipal = authorizationService.currentUserPrincipal();
        if (userPrincipal != null) {
            return userPrincipal.userId() == null
                    ? userPrincipal.username()
                    : userPrincipal.userId().toString();
        }
        return "system";
    }
}
