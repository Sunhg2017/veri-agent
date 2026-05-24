package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.management.api.response.ApplicationView;
import com.songhg.veri.agent.management.api.response.AuditLogView;
import com.songhg.veri.agent.management.api.response.AuditOutboxView;
import com.songhg.veri.agent.management.api.request.CreateApplicationRequest;
import com.songhg.veri.agent.management.api.request.CreateEnvironmentRequest;
import com.songhg.veri.agent.management.api.request.CreateIntegrationRequest;
import com.songhg.veri.agent.management.api.request.CreateProjectRequest;
import com.songhg.veri.agent.management.api.request.CreateRoleRequest;
import com.songhg.veri.agent.management.api.request.CreateSecretReferenceRequest;
import com.songhg.veri.agent.management.api.request.CreateSettingRequest;
import com.songhg.veri.agent.management.api.request.DisableSecretReferenceRequest;
import com.songhg.veri.agent.management.api.response.DepartmentView;
import com.songhg.veri.agent.management.api.response.EnvironmentConnectivityCheckView;
import com.songhg.veri.agent.management.api.response.EnvironmentView;
import com.songhg.veri.agent.management.api.response.IntegrationView;
import com.songhg.veri.agent.management.api.response.PermissionView;
import com.songhg.veri.agent.management.api.response.ProjectView;
import com.songhg.veri.agent.management.api.request.ProjectMemberRequest;
import com.songhg.veri.agent.management.api.request.RotateSecretReferenceRequest;
import com.songhg.veri.agent.management.api.response.ProjectMemberView;
import com.songhg.veri.agent.management.api.response.RoleDetailView;
import com.songhg.veri.agent.management.api.response.RoleView;
import com.songhg.veri.agent.management.api.request.ScopedUserRoleRequest;
import com.songhg.veri.agent.management.api.response.ScopedUserRoleView;
import com.songhg.veri.agent.management.api.response.SecretReferenceView;
import com.songhg.veri.agent.management.api.response.SettingView;
import com.songhg.veri.agent.management.api.request.UpdateDepartmentRequest;
import com.songhg.veri.agent.management.api.request.UpdateApplicationRequest;
import com.songhg.veri.agent.management.api.request.UpdateEnvironmentRequest;
import com.songhg.veri.agent.management.api.request.UpdateIntegrationRequest;
import com.songhg.veri.agent.management.api.request.UpdateProjectRequest;
import com.songhg.veri.agent.management.api.request.UpdateRoleRequest;
import com.songhg.veri.agent.management.api.request.UpdateSettingRequest;
import com.songhg.veri.agent.management.api.request.UpdateUserRequest;
import com.songhg.veri.agent.management.api.response.UserView;
import com.songhg.veri.agent.management.application.AuditLogQuery;
import com.songhg.veri.agent.management.application.AuditOutboxQuery;
import com.songhg.veri.agent.management.application.EnvironmentConnectivityChecker;
import com.songhg.veri.agent.management.application.ManagementConsoleService;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("local")
@Service
public class InMemoryManagementConsoleService implements ManagementConsoleService {

    private final InMemoryManagementDepartmentService departmentService;
    private final InMemoryManagementUserService userService;
    private final InMemoryManagementRoleService roleService;
    private final InMemoryManagementProjectService projectService;
    private final InMemoryManagementApplicationService applicationService;
    private final InMemoryManagementEnvironmentService environmentService;
    private final InMemoryManagementIntegrationService integrationService;
    private final InMemoryManagementAuditQueryService auditQueryService;
    private final InMemoryManagementConfigService configService;
    private final InMemoryManagementSecretReferenceService secretReferenceService;

    public InMemoryManagementConsoleService(
            AuditLogWriter auditLogWriter,
            EnvironmentConnectivityChecker connectivityChecker
    ) {
        departmentService = new InMemoryManagementDepartmentService(auditLogWriter);
        userService = new InMemoryManagementUserService(auditLogWriter);
        roleService = new InMemoryManagementRoleService(userService, auditLogWriter);
        projectService = new InMemoryManagementProjectService(userService, auditLogWriter);
        applicationService = new InMemoryManagementApplicationService(userService, auditLogWriter);
        environmentService = new InMemoryManagementEnvironmentService(userService, auditLogWriter, connectivityChecker);
        integrationService = new InMemoryManagementIntegrationService(auditLogWriter);
        auditQueryService = new InMemoryManagementAuditQueryService(auditLogWriter);
        configService = new InMemoryManagementConfigService(auditLogWriter);
        secretReferenceService = new InMemoryManagementSecretReferenceService(auditLogWriter);
    }

    @Override
    public synchronized PageResponse<DepartmentView> departments(PageQuery pageQuery) {
        return departmentService.departments(pageQuery);
    }

    @Override
    public synchronized DepartmentView createDepartment(String name, AuthUserPrincipal actor) {
        return departmentService.createDepartment(name, actor);
    }

    @Override
    public synchronized DepartmentView department(String key) {
        return departmentService.department(key);
    }

    @Override
    public synchronized DepartmentView updateDepartment(String key, UpdateDepartmentRequest request, AuthUserPrincipal actor) {
        return departmentService.updateDepartment(key, request, actor);
    }

    @Override
    public synchronized DepartmentView changeDepartmentStatus(String key, String status, AuthUserPrincipal actor) {
        return departmentService.changeDepartmentStatus(key, status, actor);
    }

    @Override
    public synchronized PageResponse<UserView> users(PageQuery pageQuery) {
        return userService.users(pageQuery);
    }

    @Override
    public synchronized UserView user(String username) {
        return userService.user(username);
    }

    @Override
    public synchronized UserView createUser(String username, AuthUserPrincipal actor) {
        return userService.createUser(username, actor);
    }

    @Override
    public synchronized UserView updateUser(String username, UpdateUserRequest request, AuthUserPrincipal actor) {
        return userService.updateUser(username, request, actor);
    }

    @Override
    public synchronized UserView enableUser(String username, AuthUserPrincipal actor) {
        return userService.enableUser(username, actor);
    }

    @Override
    public synchronized UserView disableUser(String username, AuthUserPrincipal actor) {
        return userService.disableUser(username, actor);
    }

    @Override
    public synchronized UserView lockUser(String username, AuthUserPrincipal actor) {
        return userService.lockUser(username, actor);
    }

    @Override
    public synchronized UserView unlockUser(String username, AuthUserPrincipal actor) {
        return userService.unlockUser(username, actor);
    }

    @Override
    public synchronized UserView resetUserPassword(String username, String newPassword, AuthUserPrincipal actor) {
        return userService.resetUserPassword(username, newPassword, actor);
    }

    @Override
    public synchronized PageResponse<RoleView> roles(PageQuery pageQuery) {
        return roleService.roles(pageQuery);
    }

    @Override
    public synchronized PageResponse<PermissionView> permissions(PageQuery pageQuery) {
        return roleService.permissions(pageQuery);
    }

    @Override
    public synchronized RoleDetailView role(String code) {
        return roleService.role(code);
    }

    @Override
    public synchronized RoleDetailView createRole(CreateRoleRequest request, Set<String> assignablePermissions, AuthUserPrincipal actor) {
        return roleService.createRole(request, assignablePermissions, actor);
    }

    @Override
    public synchronized RoleDetailView updateRole(String code, UpdateRoleRequest request, Set<String> assignablePermissions, AuthUserPrincipal actor) {
        return roleService.updateRole(code, request, assignablePermissions, actor);
    }

    @Override
    public synchronized RoleDetailView changeRoleStatus(String code, String status, AuthUserPrincipal actor) {
        return roleService.changeRoleStatus(code, status, actor);
    }

    @Override
    public synchronized UserView assignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        return roleService.assignUserRole(username, roleCode, actor);
    }

    @Override
    public synchronized UserView unassignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        return roleService.unassignUserRole(username, roleCode, actor);
    }

    @Override
    public synchronized PageResponse<ProjectView> projects(PageQuery pageQuery, AuthUserPrincipal actor) {
        return projectService.projects(pageQuery, actor);
    }

    @Override
    public synchronized ProjectView project(String key) {
        return projectService.project(key);
    }

    @Override
    public synchronized ProjectView createProject(CreateProjectRequest request, AuthUserPrincipal actor) {
        return projectService.createProject(request, actor);
    }

    @Override
    public synchronized ProjectView updateProject(String key, UpdateProjectRequest request, AuthUserPrincipal actor) {
        return projectService.updateProject(key, request, actor);
    }

    @Override
    public synchronized ProjectView changeProjectStatus(String key, String status, AuthUserPrincipal actor) {
        return projectService.changeProjectStatus(key, status, actor);
    }

    @Override
    public synchronized PageResponse<ProjectMemberView> projectMembers(String projectKey, PageQuery pageQuery) {
        return projectService.projectMembers(projectKey, pageQuery);
    }

    @Override
    public synchronized ProjectMemberView addProjectMember(String projectKey, ProjectMemberRequest request, AuthUserPrincipal actor) {
        return projectService.addProjectMember(projectKey, request, actor);
    }

    @Override
    public synchronized ProjectMemberView removeProjectMember(String projectKey, String username, AuthUserPrincipal actor) {
        return projectService.removeProjectMember(projectKey, username, actor);
    }

    @Override
    public synchronized PageResponse<ApplicationView> applications(PageQuery pageQuery, AuthUserPrincipal actor) {
        return applicationService.applications(pageQuery, actor);
    }

    @Override
    public synchronized ApplicationView application(String key) {
        return applicationService.application(key);
    }

    @Override
    public synchronized ApplicationView createApplication(CreateApplicationRequest request, AuthUserPrincipal actor) {
        return applicationService.createApplication(request, actor);
    }

    @Override
    public synchronized ApplicationView updateApplication(String key, UpdateApplicationRequest request, AuthUserPrincipal actor) {
        return applicationService.updateApplication(key, request, actor);
    }

    @Override
    public synchronized ApplicationView changeApplicationStatus(String key, String status, AuthUserPrincipal actor) {
        return applicationService.changeApplicationStatus(key, status, actor);
    }

    @Override
    public synchronized PageResponse<ScopedUserRoleView> applicationOwners(String applicationKey, PageQuery pageQuery) {
        return applicationService.applicationOwners(applicationKey, pageQuery);
    }

    @Override
    public synchronized ScopedUserRoleView addApplicationOwner(String applicationKey, ScopedUserRoleRequest request, AuthUserPrincipal actor) {
        return applicationService.addApplicationOwner(applicationKey, request, actor);
    }

    @Override
    public synchronized ScopedUserRoleView removeApplicationOwner(String applicationKey, String username, AuthUserPrincipal actor) {
        return applicationService.removeApplicationOwner(applicationKey, username, actor);
    }

    @Override
    public synchronized PageResponse<EnvironmentView> environments(PageQuery pageQuery, AuthUserPrincipal actor) {
        return environmentService.environments(pageQuery, actor);
    }

    @Override
    public synchronized EnvironmentView environment(String key) {
        return environmentService.environment(key);
    }

    @Override
    public synchronized EnvironmentView createEnvironment(CreateEnvironmentRequest request, AuthUserPrincipal actor) {
        return environmentService.createEnvironment(request, actor);
    }

    @Override
    public synchronized EnvironmentView updateEnvironment(String key, UpdateEnvironmentRequest request, AuthUserPrincipal actor) {
        return environmentService.updateEnvironment(key, request, actor);
    }

    @Override
    public synchronized EnvironmentView changeEnvironmentStatus(String key, String status, AuthUserPrincipal actor) {
        return environmentService.changeEnvironmentStatus(key, status, actor);
    }

    @Override
    public synchronized EnvironmentConnectivityCheckView environmentConnectivityCheck(String key) {
        return environmentService.environmentConnectivityCheck(key);
    }

    @Override
    public synchronized EnvironmentConnectivityCheckView checkEnvironmentConnectivity(String key, AuthUserPrincipal actor) {
        return environmentService.checkEnvironmentConnectivity(key, actor);
    }

    @Override
    public synchronized PageResponse<ScopedUserRoleView> environmentUsers(String environmentKey, PageQuery pageQuery) {
        return environmentService.environmentUsers(environmentKey, pageQuery);
    }

    @Override
    public synchronized ScopedUserRoleView addEnvironmentUser(String environmentKey, ScopedUserRoleRequest request, AuthUserPrincipal actor) {
        return environmentService.addEnvironmentUser(environmentKey, request, actor);
    }

    @Override
    public synchronized ScopedUserRoleView removeEnvironmentUser(String environmentKey, String username, AuthUserPrincipal actor) {
        return environmentService.removeEnvironmentUser(environmentKey, username, actor);
    }

    @Override
    public synchronized PageResponse<IntegrationView> integrations(PageQuery pageQuery) {
        return integrationService.integrations(pageQuery);
    }

    @Override
    public synchronized IntegrationView integration(String key) {
        return integrationService.integration(key);
    }

    @Override
    public synchronized IntegrationView createIntegration(CreateIntegrationRequest request, AuthUserPrincipal actor) {
        return integrationService.createIntegration(request, actor);
    }

    @Override
    public synchronized IntegrationView updateIntegration(String key, UpdateIntegrationRequest request, AuthUserPrincipal actor) {
        return integrationService.updateIntegration(key, request, actor);
    }

    @Override
    public synchronized IntegrationView changeIntegrationStatus(String key, String status, AuthUserPrincipal actor) {
        return integrationService.changeIntegrationStatus(key, status, actor);
    }

    @Override
    public synchronized PageResponse<AuditLogView> auditLogs(PageQuery pageQuery, AuditLogQuery query, AuthUserPrincipal actor) {
        return auditQueryService.auditLogs(pageQuery, query, actor);
    }

    @Override
    public synchronized String exportAuditLogsCsv(AuditLogQuery query, AuthUserPrincipal actor) {
        return auditQueryService.exportAuditLogsCsv(query, actor);
    }

    @Override
    public synchronized PageResponse<AuditOutboxView> auditOutbox(
            PageQuery pageQuery,
            AuditOutboxQuery query,
            AuthUserPrincipal actor
    ) {
        return auditQueryService.auditOutbox(pageQuery, query, actor);
    }

    @Override
    public synchronized PageResponse<SettingView> settings(PageQuery pageQuery) {
        return configService.settings(pageQuery);
    }

    @Override
    public synchronized SettingView setting(String key) {
        return configService.setting(key);
    }

    @Override
    public synchronized SettingView createSetting(CreateSettingRequest request, AuthUserPrincipal actor) {
        return configService.createSetting(request, actor);
    }

    @Override
    public synchronized SettingView updateSetting(String key, UpdateSettingRequest request, AuthUserPrincipal actor) {
        return configService.updateSetting(key, request, actor);
    }

    @Override
    public synchronized SettingView changeSettingStatus(String key, String status, AuthUserPrincipal actor) {
        return configService.changeSettingStatus(key, status, actor);
    }

    @Override
    public synchronized PageResponse<SecretReferenceView> secrets(PageQuery pageQuery) {
        return secretReferenceService.secrets(pageQuery);
    }

    @Override
    public synchronized SecretReferenceView createSecret(CreateSecretReferenceRequest request, AuthUserPrincipal actor) {
        return secretReferenceService.createSecret(request, actor);
    }

    @Override
    public synchronized SecretReferenceView rotateSecret(RotateSecretReferenceRequest request, AuthUserPrincipal actor) {
        return secretReferenceService.rotateSecret(request, actor);
    }

    @Override
    public synchronized SecretReferenceView disableSecret(DisableSecretReferenceRequest request, AuthUserPrincipal actor) {
        return secretReferenceService.disableSecret(request, actor);
    }

}
