package com.songhg.veri.agent.authorization.infrastructure;

import java.util.Map;
import java.util.Set;

final class BuiltinPermissionCatalog {

    private BuiltinPermissionCatalog() {
    }

    static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            "SuperAdmin", Set.of(
                    "role:read", "role:create", "role:edit", "role:bind", "role:unbind",
                    "audit:read", "audit:export", "audit:write_internal",
                    "context:read", "context:switch", "context:effective_read",
                    "department:read", "department:create", "department:edit", "department:enable",
                    "department:disable", "department:member_manage",
                    "user:read", "user:create", "user:edit", "user:enable", "user:disable",
                    "user:lock", "user:unlock", "user:assign_role", "user:reset_password",
                    "project:read", "project:create", "project:edit", "project:archive",
                    "project:disable", "project:member_manage",
                    "application:read", "application:create", "application:edit", "application:disable",
                    "application:owner_manage",
                    "environment:read", "environment:create", "environment:edit", "environment:disable",
                    "environment:use", "environment:user_manage",
                    "config:read", "config:edit"
            ),
            "PlatformAdmin", Set.of(
                    "department:read", "department:create", "department:edit", "department:enable",
                    "department:disable", "department:member_manage",
                    "user:read", "user:create", "user:edit", "user:enable", "user:disable",
                    "user:lock", "user:unlock", "user:assign_role", "user:reset_password",
                    "role:read", "role:bind", "role:unbind",
                    "project:read", "project:create", "project:edit", "project:archive",
                    "project:disable", "project:member_manage",
                    "application:read", "application:create", "application:edit", "application:disable",
                    "application:owner_manage",
                    "environment:read", "environment:create", "environment:edit", "environment:disable",
                    "environment:use", "environment:user_manage",
                    "config:read", "config:edit", "audit:read", "audit:export", "secret:reference",
                    "context:read", "context:switch", "context:effective_read"
            ),
            "DepartmentManager", Set.of(
                    "department:read", "department:edit", "department:enable", "department:disable",
                    "department:member_manage",
                    "user:read", "user:edit", "project:read", "application:read", "environment:read",
                    "config:read", "audit:read",
                    "context:read", "context:switch", "context:effective_read"
            ),
            "ProjectOwner", Set.of(
                    "project:read", "project:edit", "project:archive", "project:disable",
                    "project:member_manage",
                    "application:read", "application:create", "application:edit", "application:disable",
                    "application:owner_manage",
                    "environment:read", "environment:create", "environment:edit", "environment:disable",
                    "environment:use", "environment:user_manage",
                    "config:read", "config:edit", "role:read", "role:bind", "role:unbind",
                    "audit:read", "secret:reference",
                    "context:read", "context:switch", "context:effective_read"
            ),
            "AppOwner", Set.of(
                    "project:read", "application:read", "application:edit", "application:disable",
                    "application:owner_manage",
                    "environment:read", "environment:create", "environment:edit", "environment:disable",
                    "environment:user_manage", "config:read", "config:edit", "role:read", "role:bind", "role:unbind",
                    "audit:read", "secret:reference",
                    "context:read", "context:switch", "context:effective_read"
            ),
            "Tester", Set.of(
                    "project:read", "application:read", "environment:read", "environment:use",
                    "config:read", "context:read", "context:switch", "context:effective_read"
            ),
            "Developer", Set.of(
                    "project:read", "application:read", "environment:read", "config:read",
                    "context:read", "context:switch", "context:effective_read"
            ),
            "Auditor", Set.of(
                    "department:read", "user:read", "project:read", "application:read",
                    "environment:read", "config:read", "role:read", "audit:read", "audit:export",
                    "context:read", "context:effective_read"
            )
    );
}
