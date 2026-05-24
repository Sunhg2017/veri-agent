package com.songhg.veri.agent.management.application;

/**
 * Management console facade kept for controller compatibility; capability-specific contracts below
 * prevent new features from continuing to grow a single unreadable service interface.
 */
public interface ManagementConsoleService extends
        DepartmentOperations,
        UserOperations,
        RoleOperations,
        ProjectOperations,
        ApplicationOperations,
        EnvironmentOperations,
        IntegrationOperations,
        AuditOperations,
        SettingOperations,
        SecretReferenceOperations {
}
