package com.songhg.veri.agent.management.application.port;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.application.command.CreateEnvironmentCommand;
import com.songhg.veri.agent.management.application.command.ScopedUserRoleCommand;
import com.songhg.veri.agent.management.application.command.UpdateEnvironmentCommand;
import com.songhg.veri.agent.management.application.view.EnvironmentConnectivityCheckView;
import com.songhg.veri.agent.management.application.view.EnvironmentView;
import com.songhg.veri.agent.management.application.view.ScopedUserRoleView;

/**
 * Environment management use cases. Environment operations are project/application scoped and must
 * keep connectivity diagnostics separate from lifecycle state changes.
 */
public interface EnvironmentOperations {

    /**
     * Lists environments visible to the actor under the configured scope rules.
     */
    PageResponse<EnvironmentView> environments(PageQuery pageQuery, AuthUserPrincipal actor);

    /**
     * Returns environment metadata by key.
     */
    EnvironmentView environment(String key);

    /**
     * Creates an environment and stores its deployment/connectivity metadata.
     */
    EnvironmentView createEnvironment(CreateEnvironmentCommand request, AuthUserPrincipal actor);

    /**
     * Updates environment metadata without performing connectivity probes.
     */
    EnvironmentView updateEnvironment(String key, UpdateEnvironmentCommand request, AuthUserPrincipal actor);

    /**
     * Applies a lifecycle status selected by the caller after dynamic permission validation.
     */
    EnvironmentView changeEnvironmentStatus(String key, String status, AuthUserPrincipal actor);

    /**
     * Returns the latest persisted connectivity check result for display.
     */
    EnvironmentConnectivityCheckView environmentConnectivityCheck(String key);

    /**
     * Performs an on-demand connectivity probe and persists the diagnostic result.
     */
    EnvironmentConnectivityCheckView checkEnvironmentConnectivity(String key, AuthUserPrincipal actor);

    /**
     * Lists users that have environment-scoped access.
     */
    PageResponse<ScopedUserRoleView> environmentUsers(String environmentKey, PageQuery pageQuery);

    /**
     * Grants environment-scoped access and records the role-binding audit event.
     */
    ScopedUserRoleView addEnvironmentUser(
            String environmentKey,
            ScopedUserRoleCommand request,
            AuthUserPrincipal actor
    );

    /**
     * Revokes environment-scoped access without changing global user status.
     */
    ScopedUserRoleView removeEnvironmentUser(String environmentKey, String username, AuthUserPrincipal actor);
}
