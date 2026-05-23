package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.secret.SecretProviderProperties;
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
import com.songhg.veri.agent.management.api.request.ProjectMemberRequest;
import com.songhg.veri.agent.management.api.request.RotateSecretReferenceRequest;
import com.songhg.veri.agent.management.api.response.ProjectMemberView;
import com.songhg.veri.agent.management.api.response.ProjectView;
import com.songhg.veri.agent.management.api.response.RoleDetailView;
import com.songhg.veri.agent.management.api.response.RoleView;
import com.songhg.veri.agent.management.api.request.ScopedUserRoleRequest;
import com.songhg.veri.agent.management.api.response.ScopedUserRoleView;
import com.songhg.veri.agent.management.api.response.SecretReferenceView;
import com.songhg.veri.agent.management.api.response.SettingView;
import com.songhg.veri.agent.management.api.request.UpdateApplicationRequest;
import com.songhg.veri.agent.management.api.request.UpdateDepartmentRequest;
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
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapper;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("db")
@Service
public class PostgresManagementConsoleService implements ManagementConsoleService {

    private final PostgresManagementDepartmentService departmentService;
    private final PostgresManagementUserService userService;
    private final PostgresManagementProjectService projectService;
    private final PostgresManagementApplicationService applicationService;
    private final PostgresManagementEnvironmentService environmentService;
    private final PostgresManagementAuditQueryService auditQueryService;
    private final PostgresManagementConfigService configService;
    private final PostgresManagementRoleService roleService;
    private final PostgresManagementSecretReferenceService secretReferenceService;

    public PostgresManagementConsoleService(
            ManagementMapper mapper,
            PasswordEncoder passwordEncoder,
            AuditLogWriter auditLogWriter,
            PostgresManagementDeniedAuditRecorder deniedAuditRecorder,
            EnvironmentConnectivityChecker connectivityChecker,
            ObjectMapper objectMapper,
            SecretProviderProperties secretProviderProperties
    ) {
        this.departmentService = new PostgresManagementDepartmentService(mapper, auditLogWriter);
        this.userService = new PostgresManagementUserService(mapper, auditLogWriter, passwordEncoder);
        this.projectService = new PostgresManagementProjectService(mapper, auditLogWriter, deniedAuditRecorder);
        this.applicationService = new PostgresManagementApplicationService(mapper, auditLogWriter, projectService);
        this.environmentService = new PostgresManagementEnvironmentService(
                mapper,
                auditLogWriter,
                connectivityChecker,
                objectMapper,
                projectService
        );
        this.auditQueryService = new PostgresManagementAuditQueryService(mapper, auditLogWriter);
        this.configService = new PostgresManagementConfigService(mapper, auditLogWriter);
        this.roleService = new PostgresManagementRoleService(mapper, auditLogWriter);
        this.secretReferenceService = new PostgresManagementSecretReferenceService(
                mapper,
                auditLogWriter,
                secretProviderProperties
        );
    }

    @Override
    public PageResponse<DepartmentView> departments(PageQuery pageQuery) {
        return departmentService.departments(pageQuery);
    }

    @Override
    @Transactional
    public DepartmentView createDepartment(String name, AuthUserPrincipal actor) {
        return departmentService.createDepartment(name, actor);
    }

    @Override
    public DepartmentView department(String key) {
        return departmentService.department(key);
    }

    @Override
    @Transactional
    public DepartmentView updateDepartment(String key, UpdateDepartmentRequest request, AuthUserPrincipal actor) {
        return departmentService.updateDepartment(key, request, actor);
    }

    @Override
    @Transactional
    public DepartmentView changeDepartmentStatus(String key, String status, AuthUserPrincipal actor) {
        return departmentService.changeDepartmentStatus(key, status, actor);
    }

    @Override
    public PageResponse<UserView> users(PageQuery pageQuery) {
        return userService.users(pageQuery);
    }

    @Override
    public UserView user(String username) {
        return userService.user(username);
    }

    @Override
    @Transactional
    public UserView createUser(String username, AuthUserPrincipal actor) {
        return userService.createUser(username, actor);
    }

    @Override
    @Transactional
    public UserView updateUser(String username, UpdateUserRequest request, AuthUserPrincipal actor) {
        return userService.updateUser(username, request, actor);
    }

    @Override
    @Transactional
    public UserView enableUser(String username, AuthUserPrincipal actor) {
        return userService.enableUser(username, actor);
    }

    @Override
    @Transactional
    public UserView disableUser(String username, AuthUserPrincipal actor) {
        return userService.disableUser(username, actor);
    }

    @Override
    @Transactional
    public UserView lockUser(String username, AuthUserPrincipal actor) {
        return userService.lockUser(username, actor);
    }

    @Override
    @Transactional
    public UserView unlockUser(String username, AuthUserPrincipal actor) {
        return userService.unlockUser(username, actor);
    }

    @Override
    @Transactional
    public UserView resetUserPassword(String username, String newPassword, AuthUserPrincipal actor) {
        return userService.resetUserPassword(username, newPassword, actor);
    }

    @Override
    public PageResponse<RoleView> roles(PageQuery pageQuery) {
        return roleService.roles(pageQuery);
    }

    @Override
    public PageResponse<PermissionView> permissions(PageQuery pageQuery) {
        return roleService.permissions(pageQuery);
    }

    @Override
    public RoleDetailView role(String code) {
        return roleService.role(code);
    }

    @Override
    @Transactional
    public RoleDetailView createRole(CreateRoleRequest request, Set<String> assignablePermissions, AuthUserPrincipal actor) {
        return roleService.createRole(request, assignablePermissions, actor);
    }

    @Override
    @Transactional
    public RoleDetailView updateRole(String code, UpdateRoleRequest request, Set<String> assignablePermissions, AuthUserPrincipal actor) {
        return roleService.updateRole(code, request, assignablePermissions, actor);
    }

    @Override
    @Transactional
    public RoleDetailView changeRoleStatus(String code, String status, AuthUserPrincipal actor) {
        return roleService.changeRoleStatus(code, status, actor);
    }

    @Override
    @Transactional
    public UserView assignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        return roleService.assignUserRole(username, roleCode, actor);
    }

    @Override
    @Transactional
    public UserView unassignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        return roleService.unassignUserRole(username, roleCode, actor);
    }

    @Override
    public PageResponse<ProjectView> projects(PageQuery pageQuery, AuthUserPrincipal actor) {
        return projectService.projects(pageQuery, actor);
    }

    @Override
    public ProjectView project(String key) {
        return projectService.project(key);
    }

    @Override
    @Transactional
    public ProjectView createProject(CreateProjectRequest request, AuthUserPrincipal actor) {
        return projectService.createProject(request, actor);
    }

    @Override
    @Transactional
    public ProjectView updateProject(String key, UpdateProjectRequest request, AuthUserPrincipal actor) {
        return projectService.updateProject(key, request, actor);
    }

    @Override
    @Transactional
    public ProjectView changeProjectStatus(String key, String status, AuthUserPrincipal actor) {
        return projectService.changeProjectStatus(key, status, actor);
    }

    @Override
    public PageResponse<ProjectMemberView> projectMembers(String projectKey, PageQuery pageQuery) {
        return projectService.projectMembers(projectKey, pageQuery);
    }

    @Override
    @Transactional
    public ProjectMemberView addProjectMember(String projectKey, ProjectMemberRequest request, AuthUserPrincipal actor) {
        return projectService.addProjectMember(projectKey, request, actor);
    }

    @Override
    @Transactional
    public ProjectMemberView removeProjectMember(String projectKey, String username, AuthUserPrincipal actor) {
        return projectService.removeProjectMember(projectKey, username, actor);
    }

    @Override
    public PageResponse<ApplicationView> applications(PageQuery pageQuery, AuthUserPrincipal actor) {
        return applicationService.applications(pageQuery, actor);
    }

    @Override
    public ApplicationView application(String key) {
        return applicationService.application(key);
    }

    @Override
    @Transactional
    public ApplicationView createApplication(CreateApplicationRequest request, AuthUserPrincipal actor) {
        return applicationService.createApplication(request, actor);
    }

    @Override
    @Transactional
    public ApplicationView updateApplication(String key, UpdateApplicationRequest request, AuthUserPrincipal actor) {
        return applicationService.updateApplication(key, request, actor);
    }

    @Override
    @Transactional
    public ApplicationView changeApplicationStatus(String key, String status, AuthUserPrincipal actor) {
        return applicationService.changeApplicationStatus(key, status, actor);
    }

    @Override
    public PageResponse<ScopedUserRoleView> applicationOwners(String applicationKey, PageQuery pageQuery) {
        return applicationService.applicationOwners(applicationKey, pageQuery);
    }

    @Override
    @Transactional
    public ScopedUserRoleView addApplicationOwner(String applicationKey, ScopedUserRoleRequest request, AuthUserPrincipal actor) {
        return applicationService.addApplicationOwner(applicationKey, request, actor);
    }

    @Override
    @Transactional
    public ScopedUserRoleView removeApplicationOwner(String applicationKey, String username, AuthUserPrincipal actor) {
        return applicationService.removeApplicationOwner(applicationKey, username, actor);
    }

    @Override
    public PageResponse<EnvironmentView> environments(PageQuery pageQuery, AuthUserPrincipal actor) {
        return environmentService.environments(pageQuery, actor);
    }

    @Override
    public EnvironmentView environment(String key) {
        return environmentService.environment(key);
    }

    @Override
    @Transactional
    public EnvironmentView createEnvironment(CreateEnvironmentRequest request, AuthUserPrincipal actor) {
        return environmentService.createEnvironment(request, actor);
    }

    @Override
    @Transactional
    public EnvironmentView updateEnvironment(String key, UpdateEnvironmentRequest request, AuthUserPrincipal actor) {
        return environmentService.updateEnvironment(key, request, actor);
    }

    @Override
    @Transactional
    public EnvironmentView changeEnvironmentStatus(String key, String status, AuthUserPrincipal actor) {
        return environmentService.changeEnvironmentStatus(key, status, actor);
    }

    @Override
    public EnvironmentConnectivityCheckView environmentConnectivityCheck(String key) {
        return environmentService.environmentConnectivityCheck(key);
    }

    @Override
    @Transactional
    public EnvironmentConnectivityCheckView checkEnvironmentConnectivity(String key, AuthUserPrincipal actor) {
        return environmentService.checkEnvironmentConnectivity(key, actor);
    }

    @Override
    public PageResponse<ScopedUserRoleView> environmentUsers(String environmentKey, PageQuery pageQuery) {
        return environmentService.environmentUsers(environmentKey, pageQuery);
    }

    @Override
    @Transactional
    public ScopedUserRoleView addEnvironmentUser(String environmentKey, ScopedUserRoleRequest request, AuthUserPrincipal actor) {
        return environmentService.addEnvironmentUser(environmentKey, request, actor);
    }

    @Override
    @Transactional
    public ScopedUserRoleView removeEnvironmentUser(String environmentKey, String username, AuthUserPrincipal actor) {
        return environmentService.removeEnvironmentUser(environmentKey, username, actor);
    }

    @Override
    public PageResponse<IntegrationView> integrations(PageQuery pageQuery) {
        return configService.integrations(pageQuery);
    }

    @Override
    public IntegrationView integration(String key) {
        return configService.integration(key);
    }

    @Override
    @Transactional
    public IntegrationView createIntegration(CreateIntegrationRequest request, AuthUserPrincipal actor) {
        return configService.createIntegration(request, actor);
    }

    @Override
    @Transactional
    public IntegrationView updateIntegration(String key, UpdateIntegrationRequest request, AuthUserPrincipal actor) {
        return configService.updateIntegration(key, request, actor);
    }

    @Override
    @Transactional
    public IntegrationView changeIntegrationStatus(String key, String status, AuthUserPrincipal actor) {
        return configService.changeIntegrationStatus(key, status, actor);
    }

    @Override
    public PageResponse<AuditLogView> auditLogs(PageQuery pageQuery, AuditLogQuery query, AuthUserPrincipal actor) {
        return auditQueryService.auditLogs(pageQuery, query, actor);
    }

    @Override
    public String exportAuditLogsCsv(AuditLogQuery query, AuthUserPrincipal actor) {
        return auditQueryService.exportAuditLogsCsv(query, actor);
    }

    @Override
    public PageResponse<AuditOutboxView> auditOutbox(PageQuery pageQuery, AuditOutboxQuery query, AuthUserPrincipal actor) {
        return auditQueryService.auditOutbox(pageQuery, query);
    }

    @Override
    public PageResponse<SettingView> settings(PageQuery pageQuery) {
        return configService.settings(pageQuery);
    }

    @Override
    public SettingView setting(String key) {
        return configService.setting(key);
    }

    @Override
    @Transactional
    public SettingView createSetting(CreateSettingRequest request, AuthUserPrincipal actor) {
        return configService.createSetting(request, actor);
    }

    @Override
    @Transactional
    public SettingView updateSetting(String key, UpdateSettingRequest request, AuthUserPrincipal actor) {
        return configService.updateSetting(key, request, actor);
    }

    @Override
    @Transactional
    public SettingView changeSettingStatus(String key, String status, AuthUserPrincipal actor) {
        return configService.changeSettingStatus(key, status, actor);
    }

    @Override
    public PageResponse<SecretReferenceView> secrets(PageQuery pageQuery) {
        return secretReferenceService.secrets(pageQuery);
    }

    @Override
    @Transactional
    public SecretReferenceView createSecret(CreateSecretReferenceRequest request, AuthUserPrincipal actor) {
        return secretReferenceService.createSecret(request, actor);
    }

    @Override
    @Transactional
    public SecretReferenceView rotateSecret(RotateSecretReferenceRequest request, AuthUserPrincipal actor) {
        return secretReferenceService.rotateSecret(request, actor);
    }

    @Override
    @Transactional
    public SecretReferenceView disableSecret(DisableSecretReferenceRequest request, AuthUserPrincipal actor) {
        return secretReferenceService.disableSecret(request, actor);
    }
}
