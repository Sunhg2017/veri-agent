package com.songhg.veri.agent.management.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.application.CreateApplicationRequest;
import com.songhg.veri.agent.management.application.ScopedUserRoleRequest;
import com.songhg.veri.agent.management.application.UpdateApplicationRequest;
import com.songhg.veri.agent.management.application.ApplicationView;
import com.songhg.veri.agent.management.application.ScopedUserRoleView;

public interface ApplicationOperations {

    PageResponse<ApplicationView> applications(PageQuery pageQuery, AuthUserPrincipal actor);

    ApplicationView application(String key);

    ApplicationView createApplication(CreateApplicationRequest request, AuthUserPrincipal actor);

    ApplicationView updateApplication(String key, UpdateApplicationRequest request, AuthUserPrincipal actor);

    ApplicationView changeApplicationStatus(String key, String status, AuthUserPrincipal actor);

    PageResponse<ScopedUserRoleView> applicationOwners(String applicationKey, PageQuery pageQuery);

    ScopedUserRoleView addApplicationOwner(
            String applicationKey,
            ScopedUserRoleRequest request,
            AuthUserPrincipal actor
    );

    ScopedUserRoleView removeApplicationOwner(String applicationKey, String username, AuthUserPrincipal actor);
}
