package com.songhg.veri.agent.management.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.application.CreateEnvironmentRequest;
import com.songhg.veri.agent.management.application.ScopedUserRoleRequest;
import com.songhg.veri.agent.management.application.UpdateEnvironmentRequest;
import com.songhg.veri.agent.management.application.EnvironmentConnectivityCheckView;
import com.songhg.veri.agent.management.application.EnvironmentView;
import com.songhg.veri.agent.management.application.ScopedUserRoleView;

public interface EnvironmentOperations {

    PageResponse<EnvironmentView> environments(PageQuery pageQuery, AuthUserPrincipal actor);

    EnvironmentView environment(String key);

    EnvironmentView createEnvironment(CreateEnvironmentRequest request, AuthUserPrincipal actor);

    EnvironmentView updateEnvironment(String key, UpdateEnvironmentRequest request, AuthUserPrincipal actor);

    EnvironmentView changeEnvironmentStatus(String key, String status, AuthUserPrincipal actor);

    EnvironmentConnectivityCheckView environmentConnectivityCheck(String key);

    EnvironmentConnectivityCheckView checkEnvironmentConnectivity(String key, AuthUserPrincipal actor);

    PageResponse<ScopedUserRoleView> environmentUsers(String environmentKey, PageQuery pageQuery);

    ScopedUserRoleView addEnvironmentUser(
            String environmentKey,
            ScopedUserRoleRequest request,
            AuthUserPrincipal actor
    );

    ScopedUserRoleView removeEnvironmentUser(String environmentKey, String username, AuthUserPrincipal actor);
}
