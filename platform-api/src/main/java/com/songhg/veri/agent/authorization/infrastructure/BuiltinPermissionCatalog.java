package com.songhg.veri.agent.authorization.infrastructure;

import com.songhg.veri.agent.authorization.application.PermissionCodes;
import java.util.Map;
import java.util.Set;

final class BuiltinPermissionCatalog {

    private BuiltinPermissionCatalog() {
    }

    static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            "SuperAdmin", Set.copyOf(PermissionCodes.ALL),
            "PlatformAdmin", Set.of(
                    PermissionCodes.DEPARTMENT_READ, PermissionCodes.DEPARTMENT_CREATE, PermissionCodes.DEPARTMENT_EDIT, PermissionCodes.DEPARTMENT_ENABLE,
                    PermissionCodes.DEPARTMENT_DISABLE, PermissionCodes.DEPARTMENT_MEMBER_MANAGE,
                    PermissionCodes.USER_READ, PermissionCodes.USER_CREATE, PermissionCodes.USER_EDIT, PermissionCodes.USER_ENABLE, PermissionCodes.USER_DISABLE,
                    PermissionCodes.USER_LOCK, PermissionCodes.USER_UNLOCK, PermissionCodes.USER_ASSIGN_ROLE, PermissionCodes.USER_RESET_PASSWORD,
                    PermissionCodes.ROLE_READ, PermissionCodes.ROLE_BIND, PermissionCodes.ROLE_UNBIND,
                    PermissionCodes.PROJECT_READ, PermissionCodes.PROJECT_CREATE, PermissionCodes.PROJECT_EDIT, PermissionCodes.PROJECT_ARCHIVE,
                    PermissionCodes.PROJECT_DISABLE, PermissionCodes.PROJECT_MEMBER_MANAGE,
                    PermissionCodes.APPLICATION_READ, PermissionCodes.APPLICATION_CREATE, PermissionCodes.APPLICATION_EDIT, PermissionCodes.APPLICATION_DISABLE,
                    PermissionCodes.APPLICATION_OWNER_MANAGE,
                    PermissionCodes.ENVIRONMENT_READ, PermissionCodes.ENVIRONMENT_CREATE, PermissionCodes.ENVIRONMENT_EDIT, PermissionCodes.ENVIRONMENT_DISABLE,
                    PermissionCodes.ENVIRONMENT_USE, PermissionCodes.ENVIRONMENT_USER_MANAGE,
                    PermissionCodes.CONFIG_READ, PermissionCodes.CONFIG_EDIT, PermissionCodes.AUDIT_READ, PermissionCodes.AUDIT_EXPORT,
                    PermissionCodes.SECRET_REFERENCE, PermissionCodes.SECRET_READ, PermissionCodes.SECRET_MANAGE, PermissionCodes.SECRET_ROTATE, PermissionCodes.SECRET_DISABLE,
                    PermissionCodes.CONTEXT_READ, PermissionCodes.CONTEXT_SWITCH, PermissionCodes.CONTEXT_EFFECTIVE_READ,
                    PermissionCodes.ASSET_READ, PermissionCodes.ASSET_MANAGE, PermissionCodes.ASSET_REVIEW, PermissionCodes.ASSET_EXPORT,
                    PermissionCodes.MODEL_ACCESS_READ, PermissionCodes.MODEL_ACCESS_MANAGE, PermissionCodes.MODEL_ACCESS_EXPORT,
                    PermissionCodes.REQUIREMENT_INPUT_READ, PermissionCodes.REQUIREMENT_INPUT_MANAGE, PermissionCodes.REQUIREMENT_INPUT_IMPORT,
                    PermissionCodes.REQUIREMENT_INPUT_CANDIDATE_REVIEW, PermissionCodes.REQUIREMENT_INPUT_PUBLISH, PermissionCodes.REQUIREMENT_INPUT_WEBHOOK_REPLAY,
                    PermissionCodes.TEST_DESIGN_READ, PermissionCodes.TEST_DESIGN_GENERATE, PermissionCodes.TEST_DESIGN_REVIEW,
                    PermissionCodes.TEST_DESIGN_PUBLISH, PermissionCodes.TEST_DESIGN_EXPORT,
                    PermissionCodes.TEST_DESIGN_POLICY_MANAGE
            ),
            "DepartmentManager", Set.of(
                    PermissionCodes.DEPARTMENT_READ, PermissionCodes.DEPARTMENT_EDIT, PermissionCodes.DEPARTMENT_ENABLE, PermissionCodes.DEPARTMENT_DISABLE,
                    PermissionCodes.DEPARTMENT_MEMBER_MANAGE,
                    PermissionCodes.USER_READ, PermissionCodes.USER_EDIT, PermissionCodes.PROJECT_READ, PermissionCodes.APPLICATION_READ, PermissionCodes.ENVIRONMENT_READ,
                    PermissionCodes.CONFIG_READ, PermissionCodes.AUDIT_READ,
                    PermissionCodes.CONTEXT_READ, PermissionCodes.CONTEXT_SWITCH, PermissionCodes.CONTEXT_EFFECTIVE_READ
            ),
            "ProjectOwner", Set.of(
                    PermissionCodes.PROJECT_READ, PermissionCodes.PROJECT_EDIT, PermissionCodes.PROJECT_ARCHIVE, PermissionCodes.PROJECT_DISABLE,
                    PermissionCodes.PROJECT_MEMBER_MANAGE,
                    PermissionCodes.APPLICATION_READ, PermissionCodes.APPLICATION_CREATE, PermissionCodes.APPLICATION_EDIT, PermissionCodes.APPLICATION_DISABLE,
                    PermissionCodes.APPLICATION_OWNER_MANAGE,
                    PermissionCodes.ENVIRONMENT_READ, PermissionCodes.ENVIRONMENT_CREATE, PermissionCodes.ENVIRONMENT_EDIT, PermissionCodes.ENVIRONMENT_DISABLE,
                    PermissionCodes.ENVIRONMENT_USE, PermissionCodes.ENVIRONMENT_USER_MANAGE,
                    PermissionCodes.CONFIG_READ, PermissionCodes.CONFIG_EDIT, PermissionCodes.ROLE_READ, PermissionCodes.ROLE_BIND, PermissionCodes.ROLE_UNBIND,
                    PermissionCodes.AUDIT_READ, PermissionCodes.SECRET_REFERENCE,
                    PermissionCodes.CONTEXT_READ, PermissionCodes.CONTEXT_SWITCH, PermissionCodes.CONTEXT_EFFECTIVE_READ,
                    PermissionCodes.ASSET_READ, PermissionCodes.ASSET_MANAGE, PermissionCodes.ASSET_REVIEW, PermissionCodes.ASSET_EXPORT,
                    PermissionCodes.REQUIREMENT_INPUT_READ, PermissionCodes.REQUIREMENT_INPUT_IMPORT,
                    PermissionCodes.REQUIREMENT_INPUT_CANDIDATE_REVIEW, PermissionCodes.REQUIREMENT_INPUT_PUBLISH,
                    PermissionCodes.TEST_DESIGN_READ, PermissionCodes.TEST_DESIGN_GENERATE, PermissionCodes.TEST_DESIGN_REVIEW,
                    PermissionCodes.TEST_DESIGN_PUBLISH, PermissionCodes.TEST_DESIGN_POLICY_MANAGE
            ),
            "AppOwner", Set.of(
                    PermissionCodes.PROJECT_READ, PermissionCodes.APPLICATION_READ, PermissionCodes.APPLICATION_EDIT, PermissionCodes.APPLICATION_DISABLE,
                    PermissionCodes.APPLICATION_OWNER_MANAGE,
                    PermissionCodes.ENVIRONMENT_READ, PermissionCodes.ENVIRONMENT_CREATE, PermissionCodes.ENVIRONMENT_EDIT, PermissionCodes.ENVIRONMENT_DISABLE,
                    PermissionCodes.ENVIRONMENT_USER_MANAGE, PermissionCodes.CONFIG_READ, PermissionCodes.CONFIG_EDIT, PermissionCodes.ROLE_READ, PermissionCodes.ROLE_BIND, PermissionCodes.ROLE_UNBIND,
                    PermissionCodes.AUDIT_READ, PermissionCodes.SECRET_REFERENCE,
                    PermissionCodes.CONTEXT_READ, PermissionCodes.CONTEXT_SWITCH, PermissionCodes.CONTEXT_EFFECTIVE_READ,
                    PermissionCodes.ASSET_READ, PermissionCodes.ASSET_MANAGE, PermissionCodes.ASSET_REVIEW,
                    PermissionCodes.REQUIREMENT_INPUT_READ, PermissionCodes.REQUIREMENT_INPUT_IMPORT,
                    PermissionCodes.REQUIREMENT_INPUT_CANDIDATE_REVIEW, PermissionCodes.REQUIREMENT_INPUT_PUBLISH,
                    PermissionCodes.TEST_DESIGN_READ, PermissionCodes.TEST_DESIGN_GENERATE, PermissionCodes.TEST_DESIGN_REVIEW,
                    PermissionCodes.TEST_DESIGN_PUBLISH
            ),
            "Tester", Set.of(
                    PermissionCodes.PROJECT_READ, PermissionCodes.APPLICATION_READ, PermissionCodes.ENVIRONMENT_READ, PermissionCodes.ENVIRONMENT_USE,
                    PermissionCodes.CONFIG_READ, PermissionCodes.CONTEXT_READ, PermissionCodes.CONTEXT_SWITCH, PermissionCodes.CONTEXT_EFFECTIVE_READ,
                    PermissionCodes.ASSET_READ, PermissionCodes.ASSET_MANAGE, PermissionCodes.ASSET_REVIEW,
                    PermissionCodes.REQUIREMENT_INPUT_READ, PermissionCodes.REQUIREMENT_INPUT_IMPORT, PermissionCodes.REQUIREMENT_INPUT_CANDIDATE_REVIEW,
                    PermissionCodes.TEST_DESIGN_READ, PermissionCodes.TEST_DESIGN_GENERATE, PermissionCodes.TEST_DESIGN_REVIEW,
                    PermissionCodes.TEST_DESIGN_PUBLISH
            ),
            "Developer", Set.of(
                    PermissionCodes.PROJECT_READ, PermissionCodes.APPLICATION_READ, PermissionCodes.ENVIRONMENT_READ, PermissionCodes.CONFIG_READ,
                    PermissionCodes.CONTEXT_READ, PermissionCodes.CONTEXT_SWITCH, PermissionCodes.CONTEXT_EFFECTIVE_READ,
                    PermissionCodes.ASSET_READ, PermissionCodes.REQUIREMENT_INPUT_READ, PermissionCodes.TEST_DESIGN_READ
            ),
            "Auditor", Set.of(
                    PermissionCodes.DEPARTMENT_READ, PermissionCodes.USER_READ, PermissionCodes.PROJECT_READ, PermissionCodes.APPLICATION_READ,
                    PermissionCodes.ENVIRONMENT_READ, PermissionCodes.CONFIG_READ, PermissionCodes.ROLE_READ, PermissionCodes.AUDIT_READ, PermissionCodes.AUDIT_EXPORT,
                    PermissionCodes.CONTEXT_READ, PermissionCodes.CONTEXT_EFFECTIVE_READ, PermissionCodes.ASSET_READ, PermissionCodes.ASSET_EXPORT,
                    PermissionCodes.MODEL_ACCESS_READ, PermissionCodes.MODEL_ACCESS_EXPORT, PermissionCodes.REQUIREMENT_INPUT_READ,
                    PermissionCodes.TEST_DESIGN_READ, PermissionCodes.TEST_DESIGN_EXPORT
            )
    );
}
