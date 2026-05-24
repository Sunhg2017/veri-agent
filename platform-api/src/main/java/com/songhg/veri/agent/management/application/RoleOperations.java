package com.songhg.veri.agent.management.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.application.CreateRoleRequest;
import com.songhg.veri.agent.management.application.UpdateRoleRequest;
import com.songhg.veri.agent.management.application.PermissionView;
import com.songhg.veri.agent.management.application.RoleDetailView;
import com.songhg.veri.agent.management.application.RoleView;
import com.songhg.veri.agent.management.application.UserView;
import java.util.Set;

public interface RoleOperations {

    PageResponse<RoleView> roles(PageQuery pageQuery);

    PageResponse<PermissionView> permissions(PageQuery pageQuery);

    RoleDetailView role(String code);

    RoleDetailView createRole(CreateRoleRequest request, Set<String> assignablePermissions, AuthUserPrincipal actor);

    RoleDetailView updateRole(
            String code,
            UpdateRoleRequest request,
            Set<String> assignablePermissions,
            AuthUserPrincipal actor
    );

    RoleDetailView changeRoleStatus(String code, String status, AuthUserPrincipal actor);

    UserView assignUserRole(String username, String roleCode, AuthUserPrincipal actor);

    UserView unassignUserRole(String username, String roleCode, AuthUserPrincipal actor);
}
