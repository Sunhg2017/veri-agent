package com.songhg.veri.agent.management.application.port;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.application.command.CreateApplicationCommand;
import com.songhg.veri.agent.management.application.command.ScopedUserRoleCommand;
import com.songhg.veri.agent.management.application.command.UpdateApplicationCommand;
import com.songhg.veri.agent.management.application.view.ApplicationView;
import com.songhg.veri.agent.management.application.view.ScopedUserRoleView;

/**
 * Application management use cases. Implementations must enforce persistence consistency and write
 * audit records for every mutation; API controllers are only responsible for transport conversion
 * and permission checks.
 */
public interface ApplicationOperations {

    /**
     * Lists applications visible to the actor, preserving project/application scope filtering.
     */
    PageResponse<ApplicationView> applications(PageQuery pageQuery, AuthUserPrincipal actor);

    /**
     * Returns a single application by key and fails when the application does not exist.
     */
    ApplicationView application(String key);

    /**
     * Creates an application under an existing project and records the actor as the mutating user.
     */
    ApplicationView createApplication(CreateApplicationCommand request, AuthUserPrincipal actor);

    /**
     * Updates editable application metadata without changing owners or lifecycle status.
     */
    ApplicationView updateApplication(String key, UpdateApplicationCommand request, AuthUserPrincipal actor);

    /**
     * Applies the requested lifecycle status; callers must already have resolved the dynamic status
     * permission code.
     */
    ApplicationView changeApplicationStatus(String key, String status, AuthUserPrincipal actor);

    /**
     * Lists users that have application-scoped ownership for delegation and review.
     */
    PageResponse<ScopedUserRoleView> applicationOwners(String applicationKey, PageQuery pageQuery);

    /**
     * Grants application-scoped ownership and emits the corresponding role-binding audit event.
     */
    ScopedUserRoleView addApplicationOwner(
            String applicationKey,
            ScopedUserRoleCommand request,
            AuthUserPrincipal actor
    );

    /**
     * Revokes application-scoped ownership without deleting the user or global role assignments.
     */
    ScopedUserRoleView removeApplicationOwner(String applicationKey, String username, AuthUserPrincipal actor);
}
