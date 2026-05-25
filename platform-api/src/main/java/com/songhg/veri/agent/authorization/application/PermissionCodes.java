package com.songhg.veri.agent.authorization.application;

import java.util.List;

/**
 * Centralizes permission code literals so API, application, and infrastructure layers share one vocabulary.
 */
public final class PermissionCodes {

    public static final String ROLE_READ = "role:read";
    public static final String ROLE_CREATE = "role:create";
    public static final String ROLE_EDIT = "role:edit";
    public static final String ROLE_BIND = "role:bind";
    public static final String ROLE_UNBIND = "role:unbind";

    public static final String AUDIT_READ = "audit:read";
    public static final String AUDIT_EXPORT = "audit:export";
    public static final String AUDIT_WRITE_INTERNAL = "audit:write_internal";

    public static final String CONTEXT_READ = "context:read";
    public static final String CONTEXT_SWITCH = "context:switch";
    public static final String CONTEXT_EFFECTIVE_READ = "context:effective_read";

    public static final String DEPARTMENT_READ = "department:read";
    public static final String DEPARTMENT_CREATE = "department:create";
    public static final String DEPARTMENT_EDIT = "department:edit";
    public static final String DEPARTMENT_ENABLE = "department:enable";
    public static final String DEPARTMENT_DISABLE = "department:disable";
    public static final String DEPARTMENT_MEMBER_MANAGE = "department:member_manage";

    public static final String USER_READ = "user:read";
    public static final String USER_CREATE = "user:create";
    public static final String USER_EDIT = "user:edit";
    public static final String USER_ENABLE = "user:enable";
    public static final String USER_DISABLE = "user:disable";
    public static final String USER_LOCK = "user:lock";
    public static final String USER_UNLOCK = "user:unlock";
    public static final String USER_ASSIGN_ROLE = "user:assign_role";
    public static final String USER_RESET_PASSWORD = "user:reset_password";

    public static final String PROJECT_READ = "project:read";
    public static final String PROJECT_CREATE = "project:create";
    public static final String PROJECT_EDIT = "project:edit";
    public static final String PROJECT_ARCHIVE = "project:archive";
    public static final String PROJECT_DISABLE = "project:disable";
    public static final String PROJECT_MEMBER_MANAGE = "project:member_manage";

    public static final String APPLICATION_READ = "application:read";
    public static final String APPLICATION_CREATE = "application:create";
    public static final String APPLICATION_EDIT = "application:edit";
    public static final String APPLICATION_DISABLE = "application:disable";
    public static final String APPLICATION_OWNER_MANAGE = "application:owner_manage";

    public static final String ENVIRONMENT_READ = "environment:read";
    public static final String ENVIRONMENT_CREATE = "environment:create";
    public static final String ENVIRONMENT_EDIT = "environment:edit";
    public static final String ENVIRONMENT_DISABLE = "environment:disable";
    public static final String ENVIRONMENT_USE = "environment:use";
    public static final String ENVIRONMENT_USER_MANAGE = "environment:user_manage";

    public static final String CONFIG_READ = "config:read";
    public static final String CONFIG_EDIT = "config:edit";

    public static final String SECRET_REFERENCE = "secret:reference";
    public static final String SECRET_READ = "secret:read";
    public static final String SECRET_MANAGE = "secret:manage";
    public static final String SECRET_ROTATE = "secret:rotate";
    public static final String SECRET_DISABLE = "secret:disable";

    public static final String ASSET_READ = "asset:read";
    public static final String ASSET_MANAGE = "asset:manage";
    public static final String ASSET_REVIEW = "asset:review";
    public static final String ASSET_EXPORT = "asset:export";

    public static final String MODEL_ACCESS_READ = "modelAccess:read";
    public static final String MODEL_ACCESS_MANAGE = "modelAccess:manage";
    public static final String MODEL_ACCESS_EXPORT = "modelAccess:export";

    public static final String REQUIREMENT_INPUT_READ = "requirementInput:read";
    public static final String REQUIREMENT_INPUT_MANAGE = "requirementInput:manage";
    public static final String REQUIREMENT_INPUT_IMPORT = "requirementInput:import";
    public static final String REQUIREMENT_INPUT_CANDIDATE_REVIEW = "requirementInput:candidate_review";
    public static final String REQUIREMENT_INPUT_PUBLISH = "requirementInput:publish";
    public static final String REQUIREMENT_INPUT_WEBHOOK_REPLAY = "requirementInput:webhook_replay";

    public static final String TEST_DESIGN_READ = "testDesign:read";
    public static final String TEST_DESIGN_GENERATE = "testDesign:generate";
    public static final String TEST_DESIGN_REVIEW = "testDesign:review";
    public static final String TEST_DESIGN_PUBLISH = "testDesign:publish";
    public static final String TEST_DESIGN_EXPORT = "testDesign:export";

    public static final List<String> ALL = List.of(
            ROLE_READ, ROLE_CREATE, ROLE_EDIT, ROLE_BIND, ROLE_UNBIND,
            AUDIT_READ, AUDIT_EXPORT, AUDIT_WRITE_INTERNAL,
            CONTEXT_READ, CONTEXT_SWITCH, CONTEXT_EFFECTIVE_READ,
            DEPARTMENT_READ, DEPARTMENT_CREATE, DEPARTMENT_EDIT, DEPARTMENT_ENABLE, DEPARTMENT_DISABLE,
            DEPARTMENT_MEMBER_MANAGE,
            USER_READ, USER_CREATE, USER_EDIT, USER_ENABLE, USER_DISABLE, USER_LOCK, USER_UNLOCK,
            USER_ASSIGN_ROLE, USER_RESET_PASSWORD,
            PROJECT_READ, PROJECT_CREATE, PROJECT_EDIT, PROJECT_ARCHIVE, PROJECT_DISABLE, PROJECT_MEMBER_MANAGE,
            APPLICATION_READ, APPLICATION_CREATE, APPLICATION_EDIT, APPLICATION_DISABLE, APPLICATION_OWNER_MANAGE,
            ENVIRONMENT_READ, ENVIRONMENT_CREATE, ENVIRONMENT_EDIT, ENVIRONMENT_DISABLE, ENVIRONMENT_USE,
            ENVIRONMENT_USER_MANAGE,
            CONFIG_READ, CONFIG_EDIT,
            SECRET_REFERENCE, SECRET_READ, SECRET_MANAGE, SECRET_ROTATE, SECRET_DISABLE,
            ASSET_READ, ASSET_MANAGE, ASSET_REVIEW, ASSET_EXPORT,
            MODEL_ACCESS_READ, MODEL_ACCESS_MANAGE, MODEL_ACCESS_EXPORT,
            REQUIREMENT_INPUT_READ, REQUIREMENT_INPUT_MANAGE, REQUIREMENT_INPUT_IMPORT,
            REQUIREMENT_INPUT_CANDIDATE_REVIEW, REQUIREMENT_INPUT_PUBLISH, REQUIREMENT_INPUT_WEBHOOK_REPLAY,
            TEST_DESIGN_READ, TEST_DESIGN_GENERATE, TEST_DESIGN_REVIEW, TEST_DESIGN_PUBLISH,
            TEST_DESIGN_EXPORT
    );

    private static final String ARCHIVED_STATUS = "ARCHIVED";
    private static final String DISABLED_STATUS = "DISABLED";

    private PermissionCodes() {
    }

    public static String departmentStatusPermission(String status) {
        return DISABLED_STATUS.equals(status) ? DEPARTMENT_DISABLE : DEPARTMENT_ENABLE;
    }

    public static String projectStatusPermission(String status) {
        if (ARCHIVED_STATUS.equals(status)) {
            return PROJECT_ARCHIVE;
        }
        return DISABLED_STATUS.equals(status) ? PROJECT_DISABLE : PROJECT_EDIT;
    }

    public static String applicationStatusPermission(String status) {
        return DISABLED_STATUS.equals(status) ? APPLICATION_DISABLE : APPLICATION_EDIT;
    }

    public static String environmentStatusPermission(String status) {
        return DISABLED_STATUS.equals(status) ? ENVIRONMENT_DISABLE : ENVIRONMENT_EDIT;
    }
}
