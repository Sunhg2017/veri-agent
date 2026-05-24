package com.songhg.veri.agent.management.application.port;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.application.command.CreateRoleRequest;
import com.songhg.veri.agent.management.application.command.UpdateRoleRequest;
import com.songhg.veri.agent.management.application.view.PermissionView;
import com.songhg.veri.agent.management.application.view.RoleDetailView;
import com.songhg.veri.agent.management.application.view.RoleView;
import com.songhg.veri.agent.management.application.view.UserView;

/**
 * Role and permission management use cases. Implementations resolve the caller's assignable
 * permission closure before changing role bindings.
 */
public interface RoleOperations {

    /**
     * Lists roles for management review.
     */
    PageResponse<RoleView> roles(PageQuery pageQuery);

    /**
     * Lists available permission codes for role composition.
     */
    PageResponse<PermissionView> permissions(PageQuery pageQuery);

    /**
     * Returns a role and its permission detail by role code.
     */
    RoleDetailView role(String code);

    /**
     * Creates a role using only permissions included in the caller's assignable closure.
     */
    RoleDetailView createRole(CreateRoleRequest request, AuthUserPrincipal actor);

    /**
     * Replaces editable role metadata and permission bindings within the assignable closure.
     */
    RoleDetailView updateRole(String code, UpdateRoleRequest request, AuthUserPrincipal actor);

    /**
     * Changes role lifecycle status without deleting historical bindings.
     */
    RoleDetailView changeRoleStatus(String code, String status, AuthUserPrincipal actor);

    /**
     * Assigns a global role to a user and records the binding audit trail.
     */
    UserView assignUserRole(String username, String roleCode, AuthUserPrincipal actor);

    /**
     * Removes a global role binding from a user without changing resource-scoped bindings.
     */
    UserView unassignUserRole(String username, String roleCode, AuthUserPrincipal actor);
}
