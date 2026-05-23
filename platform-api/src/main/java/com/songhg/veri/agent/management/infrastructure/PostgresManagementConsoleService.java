package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
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
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.ApplicationRef;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.ProjectRef;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("db")
@Service
public class PostgresManagementConsoleService implements ManagementConsoleService {

    private final ManagementMapper mapper;
    private final AuditLogWriter auditLogWriter;
    private final PostgresManagementDepartmentService departmentService;
    private final PostgresManagementUserService userService;
    private final PostgresManagementProjectService projectService;
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
        this.mapper = mapper;
        this.auditLogWriter = auditLogWriter;
        this.departmentService = new PostgresManagementDepartmentService(mapper, auditLogWriter);
        this.userService = new PostgresManagementUserService(mapper, auditLogWriter, passwordEncoder);
        this.projectService = new PostgresManagementProjectService(mapper, auditLogWriter, deniedAuditRecorder);
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
        return page(mapper::listApplications, mapper::countApplications, pageQuery, scope(actor));
    }

    @Override
    public ApplicationView application(String key) {
        return applicationByKey(key);
    }

    @Override
    @Transactional
    public ApplicationView createApplication(CreateApplicationRequest request, AuthUserPrincipal actor) {
        String name = request.name().trim();
        String appType = normalizedOrDefault(request.appType(), "Web");
        String code = normalizedOrGeneratedCode(request.code(), "app");
        String sensitivityLevel = normalizedOrDefault(request.sensitivityLevel(), "INTERNAL");
        boolean allowPublicModel = Boolean.TRUE.equals(request.allowPublicModel());
        ProjectRef project = resolveProject(request.project(), actor);
        ensureProjectEditable(project.status());
        UUID appId = UUID.randomUUID();
        try {
            update(mapper::insertApplication, actor, values(
                    "appId", appId,
                    "projectId", project.id(),
                    "code", code,
                    "name", name,
                    "appType", appType,
                    "defaultWebUrl", blankToNull(request.defaultWebUrl()),
                    "defaultApiBaseUrl", blankToNull(request.defaultApiBaseUrl()),
                    "sensitivityLevel", sensitivityLevel,
                    "allowPublicModel", allowPublicModel
            ));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "应用编码已存在");
        }
        audit(actor, "登记应用", "application", appId.toString(), name);
        return new ApplicationView(name, appType, project.name(), "v0", "已接入");
    }

    @Override
    @Transactional
    public ApplicationView updateApplication(String key, UpdateApplicationRequest request, AuthUserPrincipal actor) {
        ApplicationRef application = resolveApplicationStrict(key);
        ensureEnabled(application.status(), "当前应用状态不允许编辑");
        ApplicationView before = applicationByKey(application.id().toString());
        try {
            update(mapper::updateApplication, actor, values(
                    "applicationId", application.id(),
                    "name", blankToNull(request.name()),
                    "appType", blankToNull(request.appType()),
                    "defaultWebUrl", blankToNull(request.defaultWebUrl()),
                    "defaultApiBaseUrl", blankToNull(request.defaultApiBaseUrl()),
                    "sensitivityLevel", blankToNull(request.sensitivityLevel()),
                    "allowPublicModel", request.allowPublicModel()
            ));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "应用编码或名称已存在");
        }
        ApplicationView updated = applicationByKey(application.id().toString());
        auditChange(actor, "更新应用", "application", application.id().toString(), updated.name(),
                nameJson(before.name()), nameJson(updated.name()), null);
        return updated;
    }

    @Override
    @Transactional
    public ApplicationView changeApplicationStatus(String key, String status, AuthUserPrincipal actor) {
        ApplicationRef application = resolveApplicationStrict(key);
        String nextStatus = normalizeEnabledStatus(status, "应用状态不支持");
        update(mapper::changeApplicationStatus, actor, values("applicationId", application.id(), "status", nextStatus));
        ApplicationView updated = applicationByKey(application.id().toString());
        audit(actor, "ENABLED".equals(nextStatus) ? "启用应用" : "停用应用", "application", application.id().toString(), updated.name());
        return updated;
    }

    @Override
    public PageResponse<ScopedUserRoleView> applicationOwners(String applicationKey, PageQuery pageQuery) {
        ApplicationRef application = resolveApplicationStrict(applicationKey);
        return scopedUserRoles(application.id(), "APPLICATION", "AppOwner", pageQuery);
    }

    @Override
    @Transactional
    public ScopedUserRoleView addApplicationOwner(String applicationKey, ScopedUserRoleRequest request, AuthUserPrincipal actor) {
        ApplicationRef application = resolveApplicationStrict(applicationKey);
        ensureEnabled(application.status(), "当前应用状态不允许维护负责人");
        String roleCode = request.roleCode().trim();
        if (!"AppOwner".equals(roleCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应用负责人只能绑定 AppOwner 角色");
        }
        String username = request.username().trim();
        UUID userId = requireUserId(username);
        UUID roleId = requireRoleId(roleCode);
        bindScopedRole(userId, roleId, roleCode, "APPLICATION", application.id(), actor);
        bumpUserAuthVersion(userId, actor);
        ScopedUserRoleView view = scopedUserRoleByUsername(application.id(), "APPLICATION", "AppOwner", username, "应用负责人不存在");
        audit(actor, "添加应用负责人", "application_owner", application.id() + ":" + userId, application.name() + ":" + username);
        return view;
    }

    @Override
    @Transactional
    public ScopedUserRoleView removeApplicationOwner(String applicationKey, String username, AuthUserPrincipal actor) {
        ApplicationRef application = resolveApplicationStrict(applicationKey);
        ScopedUserRoleView current = scopedUserRoleByUsername(application.id(), "APPLICATION", "AppOwner", username, "应用负责人不存在");
        UUID userId = requireUserId(username);
        disableScopedRoles(userId, "APPLICATION", application.id(), "AppOwner", actor);
        bumpUserAuthVersion(userId, actor);
        audit(actor, "移除应用负责人", "application_owner", application.id() + ":" + userId, application.name() + ":" + username);
        return new ScopedUserRoleView(current.username(), current.displayName(), current.role(), current.scopeType(), "已移除");
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

    private ProjectRef resolveProject(String project, AuthUserPrincipal actor) {
        return projectService.resolveProject(project, actor);
    }

    private ApplicationRef resolveApplicationStrict(String key) {
        return requireOne(mapper::findApplicationRef, values("keyword", key), "应用不存在");
    }

    private ApplicationView applicationByKey(String key) {
        return requireOne(mapper::findApplicationView, values("keyword", key), "应用不存在");
    }

    private PageResponse<ScopedUserRoleView> scopedUserRoles(
            UUID scopeId,
            String scopeType,
            String roleCode,
            PageQuery pageQuery
    ) {
        return page(mapper::listScopedUserRoles, mapper::countScopedUserRoles, pageQuery, values(
                "scopeId", scopeId,
                "scopeType", scopeType,
                "roleCode", normalizeSearch(roleCode)
        ));
    }

    private ScopedUserRoleView scopedUserRoleByUsername(
            UUID scopeId,
            String scopeType,
            String roleCode,
            String username,
            String notFoundMessage
    ) {
        return requireOne(mapper::findScopedUserRoleByUsername, values(
                "scopeId", scopeId,
                "scopeType", scopeType,
                "roleCode", normalizeSearch(roleCode),
                "username", username
        ), notFoundMessage);
    }

    private void bindScopedRole(
            UUID userId,
            UUID roleId,
            String roleCode,
            String scopeType,
            UUID scopeId,
            AuthUserPrincipal actor
    ) {
        update(mapper::bindScopedRole, actor, values(
                "userId", userId,
                "roleId", roleId,
                "roleCode", roleCode,
                "scopeType", scopeType,
                "scopeId", scopeId
        ));
    }

    private void disableScopedRoles(
            UUID userId,
            String scopeType,
            UUID scopeId,
            String roleCode,
            AuthUserPrincipal actor
    ) {
        update(mapper::disableScopedRoles, actor, values(
                "userId", userId,
                "scopeType", scopeType,
                "scopeId", scopeId,
                "roleCode", normalizeSearch(roleCode)
        ));
    }

    /**
     * Write a success audit event with the given action, resource, and target name.
     * Uses AuditLogWriter (the canonical audit write path) instead of raw SQL,
     * resolving the dual-write-path issue. The afterJson captures the current state.
     */
    private void audit(
            AuthUserPrincipal actor,
            String action,
            String resourceType,
            String resourceId,
            String targetName
    ) {
        auditLogWriter.record(AuditLogWriter.success(
                actor, action, resourceType, resourceId, targetName
        ));
    }

    /**
     * Write a change audit event with before/after JSON for change tracking.
     * This enables the PRD-required "变更前摘要" and "变更后摘要" in audit logs.
     */
    private void auditChange(
            AuthUserPrincipal actor,
            String action,
            String resourceType,
            String resourceId,
            String targetName,
            String beforeJson,
            String afterJson,
            String diffJson
    ) {
        auditLogWriter.record(AuditLogWriter.changed(
                actor, action, resourceType, resourceId, targetName,
                beforeJson, afterJson, diffJson
        ));
    }

    /**
     * Build a simple {"name":"..."} JSON for after-state capture.
     */
    private String nameJson(String name) {
        return "{\"name\":\"" + escapeJson(name) + "\"}";
    }

    private boolean hasPlatformScope(AuthUserPrincipal actor) {
        return actor.roles().stream().anyMatch(role -> List.of("SuperAdmin", "PlatformAdmin", "Auditor").contains(role));
    }

    private String normalizeSearch(String search) {
        return search == null ? "" : search.trim();
    }

    private UUID requireUserId(String username) {
        return requireOne(mapper::findUserId, values("username", username), "用户不存在");
    }

    private UUID requireRoleId(String roleCode) {
        return requireOne(mapper::findRoleId, values("roleCode", roleCode), "角色不存在");
    }

    private void bumpUserAuthVersion(UUID userId, AuthUserPrincipal actor) {
        update(mapper::bumpUserAuthVersion, actor, values("userId", userId));
    }

    private int update(ToIntFunction<Map<String, Object>> statement, AuthUserPrincipal actor, Map<String, Object> params) {
        return statement.applyAsInt(withActor(actor, params));
    }

    private <T> PageResponse<T> page(
            Function<Map<String, Object>, List<T>> listStatement,
            ToLongFunction<Map<String, Object>> countStatement,
            PageQuery pageQuery,
            Map<String, Object> extraParams
    ) {
        Map<String, Object> params = pageParams(pageQuery, extraParams);
        List<T> items = listStatement.apply(params);
        long total = countStatement.applyAsLong(params);
        return PageResponse.of(items, pageQuery.index(), pageQuery.size(), total);
    }

    private Map<String, Object> pageParams(PageQuery pageQuery, Map<String, Object> extraParams) {
        Map<String, Object> params = new HashMap<>(extraParams);
        params.put("search", pageQuery.search());
        params.put("searchPattern", pageQuery.searchPattern());
        params.put("limit", pageQuery.size());
        params.put("offset", pageQuery.offset());
        return params;
    }

    private Map<String, Object> scope(AuthUserPrincipal actor) {
        return values("actorId", actor.userId(), "platformScope", hasPlatformScope(actor));
    }

    private Map<String, Object> withActor(AuthUserPrincipal actor, Map<String, Object> source) {
        Map<String, Object> params = new HashMap<>(source);
        params.put("actorId", actor.userId());
        return params;
    }

    private Map<String, Object> values(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("参数必须成对出现");
        }
        Map<String, Object> params = new HashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            params.put((String) pairs[index], pairs[index + 1]);
        }
        return params;
    }

    private <T> T requireOne(Function<Map<String, Object>, T> statement, Map<String, Object> params, String notFoundMessage) {
        Map<String, Object> normalized = new HashMap<>(params);
        if (normalized.containsKey("keyword")) {
            String keyword = blankToNull((String) normalized.get("keyword"));
            if (keyword == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage);
            }
            normalized.put("keyword", keyword);
        }
        T value = statement.apply(normalized);
        if (value == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage);
        }
        return value;
    }

    private String normalizedOrGeneratedCode(String value, String prefix) {
        return normalizedOrDefault(value, nextCode(prefix));
    }

    private String normalizedOrDefault(String value, String defaultValue) {
        String normalized = blankToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void ensureProjectEditable(String status) {
        if ("ARCHIVED".equals(status) || "DISABLED".equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前项目状态不允许新增或编辑资源");
        }
    }

    private void ensureEnabled(String status, String message) {
        if (!"ENABLED".equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, message);
        }
    }

    private String normalizeEnabledStatus(String status, String message) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("ENABLED", "DISABLED").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private String nextCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

}
