package com.songhg.veri.agent.apiautomation.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.authorization.application.AuthorizationService;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApiAutomationActorResolver {

    private final AuthorizationService authorizationService;

    public ApiAutomationActorResolver(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public String currentActor() {
        ServicePrincipal servicePrincipal = authorizationService.currentServicePrincipal();
        if (servicePrincipal != null && StringUtils.hasText(servicePrincipal.delegatedUserId())) {
            return servicePrincipal.delegatedUserId();
        }
        AuthUserPrincipal userPrincipal = authorizationService.currentUserPrincipal();
        if (userPrincipal != null) {
            return userPrincipal.userId() == null ? userPrincipal.username() : userPrincipal.userId().toString();
        }
        return "system";
    }
}
