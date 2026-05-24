package com.songhg.veri.agent.management.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.api.request.CreateEnvironmentRequest;
import com.songhg.veri.agent.management.api.request.ScopedUserRoleRequest;
import com.songhg.veri.agent.management.api.request.UpdateEnvironmentRequest;
import com.songhg.veri.agent.management.api.response.EnvironmentConnectivityCheckView;
import com.songhg.veri.agent.management.api.response.EnvironmentView;
import com.songhg.veri.agent.management.api.response.ScopedUserRoleView;

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
