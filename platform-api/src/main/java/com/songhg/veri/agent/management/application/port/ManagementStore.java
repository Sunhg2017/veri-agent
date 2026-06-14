package com.songhg.veri.agent.management.application.port;

import com.songhg.veri.agent.management.application.view.ApplicationView;
import com.songhg.veri.agent.management.application.view.AuditLogView;
import com.songhg.veri.agent.management.application.view.AuditOutboxView;
import com.songhg.veri.agent.management.application.view.DepartmentView;
import com.songhg.veri.agent.management.application.view.EnvironmentView;
import com.songhg.veri.agent.management.application.view.IntegrationView;
import com.songhg.veri.agent.management.application.view.PermissionView;
import com.songhg.veri.agent.management.application.view.ProjectMemberView;
import com.songhg.veri.agent.management.application.view.ProjectView;
import com.songhg.veri.agent.management.application.view.RoleView;
import com.songhg.veri.agent.management.application.view.ScopedUserRoleView;
import com.songhg.veri.agent.management.application.view.SecretReferenceView;
import com.songhg.veri.agent.management.application.view.UserView;
import com.songhg.veri.agent.management.application.port.ManagementQueries.ApplicationQuery;
import com.songhg.veri.agent.management.application.port.ManagementQueries.DepartmentQuery;
import com.songhg.veri.agent.management.application.port.ManagementQueries.EnvironmentQuery;
import com.songhg.veri.agent.management.application.port.ManagementQueries.ProjectQuery;
import com.songhg.veri.agent.management.application.port.ManagementQueries.UserQuery;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.ApplicationRef;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.DepartmentRef;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentConnectivityTargetRow;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentRef;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentRuntimeRef;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.IntegrationRow;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.ProjectRef;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.RoleRow;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.SecretProviderRow;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.SecretReferenceRow;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.SettingRow;
import java.util.List;
import java.util.UUID;

/**
 * Persistence port used by management application services. Business rules stay in the services;
 * the production implementation is the db/MyBatis adapter, while controller integration data lives
 * in test SQL fixtures instead of a runtime in-memory management store.
 */
public interface ManagementStore {

    default List<DepartmentView> listDepartments(DepartmentQuery query) {
        return listDepartments(query.toParams());
    }

    default long countDepartments(DepartmentQuery query) {
        return countDepartments(query.toParams());
    }

    default List<UserView> listUsers(UserQuery query) {
        return listUsers(query.toParams());
    }

    default long countUsers(UserQuery query) {
        return countUsers(query.toParams());
    }

    default List<ProjectView> listProjects(ProjectQuery query) {
        return listProjects(query.toParams());
    }

    default long countProjects(ProjectQuery query) {
        return countProjects(query.toParams());
    }

    default List<ApplicationView> listApplications(ApplicationQuery query) {
        return listApplications(query.toParams());
    }

    default long countApplications(ApplicationQuery query) {
        return countApplications(query.toParams());
    }

    default List<EnvironmentView> listEnvironments(EnvironmentQuery query) {
        return listEnvironments(query.toParams());
    }

    default long countEnvironments(EnvironmentQuery query) {
        return countEnvironments(query.toParams());
    }

    List<DepartmentView> listDepartments(ManagementStoreParams params);

    long countDepartments(ManagementStoreParams params);

    int insertDepartment(ManagementStoreParams params);

    int updateDepartment(ManagementStoreParams params);

    int changeDepartmentStatus(ManagementStoreParams params);

    List<UserView> listUsers(ManagementStoreParams params);

    long countUsers(ManagementStoreParams params);

    int insertUser(ManagementStoreParams params);

    int updateUser(ManagementStoreParams params);

    int enableUser(ManagementStoreParams params);

    int disableUser(ManagementStoreParams params);

    int lockUser(ManagementStoreParams params);

    int unlockUser(ManagementStoreParams params);

    int resetUserPassword(ManagementStoreParams params);

    List<RoleView> listRoles(ManagementStoreParams params);

    long countRoles(ManagementStoreParams params);

    List<PermissionView> listPermissions(ManagementStoreParams params);

    long countPermissions(ManagementStoreParams params);

    RoleRow findRoleRow(ManagementStoreParams params);

    List<String> listRolePermissionCodes(ManagementStoreParams params);

    List<String> listEnabledPermissionCodes(ManagementStoreParams params);

    int insertRole(ManagementStoreParams params);

    int updateRole(ManagementStoreParams params);

    int changeRoleStatus(ManagementStoreParams params);

    int softDeleteRolePermissions(ManagementStoreParams params);

    int insertRolePermissions(ManagementStoreParams params);

    int bumpUsersAuthVersionByRole(ManagementStoreParams params);

    int assignUserRole(ManagementStoreParams params);

    int unassignUserRole(ManagementStoreParams params);

    List<ProjectView> listProjects(ManagementStoreParams params);

    long countProjects(ManagementStoreParams params);

    int insertProject(ManagementStoreParams params);

    int updateProject(ManagementStoreParams params);

    int changeProjectStatus(ManagementStoreParams params);

    List<ProjectMemberView> listProjectMembers(ManagementStoreParams params);

    long countProjectMembers(ManagementStoreParams params);

    int upsertProjectMember(ManagementStoreParams params);

    int deleteProjectMember(ManagementStoreParams params);

    int disableProjectRoleBindings(ManagementStoreParams params);

    List<ApplicationView> listApplications(ManagementStoreParams params);

    long countApplications(ManagementStoreParams params);

    int insertApplication(ManagementStoreParams params);

    int updateApplication(ManagementStoreParams params);

    int changeApplicationStatus(ManagementStoreParams params);

    List<EnvironmentView> listEnvironments(ManagementStoreParams params);

    long countEnvironments(ManagementStoreParams params);

    int insertEnvironment(ManagementStoreParams params);

    int updateEnvironment(ManagementStoreParams params);

    int changeEnvironmentStatus(ManagementStoreParams params);

    EnvironmentConnectivityTargetRow findEnvironmentConnectivityTarget(ManagementStoreParams params);

    int updateEnvironmentHealthCheck(ManagementStoreParams params);

    List<IntegrationView> listIntegrations(ManagementStoreParams params);

    long countIntegrations(ManagementStoreParams params);

    List<AuditLogView> listAuditLogs(ManagementStoreParams params);

    long countAuditLogs(ManagementStoreParams params);

    List<AuditOutboxView> listAuditOutbox(ManagementStoreParams params);

    long countAuditOutbox(ManagementStoreParams params);

    List<SettingRow> listSettings(ManagementStoreParams params);

    long countSettings(ManagementStoreParams params);

    List<SecretReferenceView> listSecretReferences(ManagementStoreParams params);

    long countSecretReferences(ManagementStoreParams params);

    int insertConfig(ManagementStoreParams params);

    SecretProviderRow findSecretProviderForManage(ManagementStoreParams params);

    SecretReferenceRow findSecretReferenceRow(ManagementStoreParams params);

    SecretReferenceView findSecretReferenceView(ManagementStoreParams params);

    int insertSecretReference(ManagementStoreParams params);

    int insertSecretLocalStore(ManagementStoreParams params);

    int updateSecretReferenceRotation(ManagementStoreParams params);

    int upsertSecretLocalStoreRotation(ManagementStoreParams params);

    int revokeSecretReference(ManagementStoreParams params);

    int revokeSecretLocalStore(ManagementStoreParams params);

    int updateIntegration(ManagementStoreParams params);

    int updateSetting(ManagementStoreParams params);

    int changeConfigStatus(ManagementStoreParams params);

    int insertProjectOwner(ManagementStoreParams params);

    int insertDepartmentManager(ManagementStoreParams params);

    UUID findDefaultProjectId(ManagementStoreParams params);

    int insertDefaultProject(ManagementStoreParams params);

    DepartmentRef findDepartmentRef(ManagementStoreParams params);

    ProjectRef findProjectRef(ManagementStoreParams params);

    ApplicationRef findApplicationRef(ManagementStoreParams params);

    EnvironmentRef findEnvironmentRef(ManagementStoreParams params);

    EnvironmentRuntimeRef findEnvironmentRuntimeRef(ManagementStoreParams params);

    DepartmentView findDepartmentView(ManagementStoreParams params);

    ProjectView findProjectView(ManagementStoreParams params);

    ApplicationView findApplicationView(ManagementStoreParams params);

    EnvironmentView findEnvironmentView(ManagementStoreParams params);

    ProjectMemberView findProjectMemberByUsername(ManagementStoreParams params);

    List<ScopedUserRoleView> listScopedUserRoles(ManagementStoreParams params);

    long countScopedUserRoles(ManagementStoreParams params);

    ScopedUserRoleView findScopedUserRoleByUsername(ManagementStoreParams params);

    ApplicationRef findApplicationRefInProject(ManagementStoreParams params);

    UUID findRoleId(ManagementStoreParams params);

    int bindRoleIfPresent(ManagementStoreParams params);

    int bindProjectRole(ManagementStoreParams params);

    int bindScopedRole(ManagementStoreParams params);

    int disableScopedRoles(ManagementStoreParams params);

    int insertAuditLog(ManagementStoreParams params);

    UserView findUserByUsername(ManagementStoreParams params);

    IntegrationRow findIntegrationRow(ManagementStoreParams params);

    SettingRow findSettingRow(ManagementStoreParams params);

    UUID findUserId(ManagementStoreParams params);

    int bumpUserAuthVersion(ManagementStoreParams params);
}
