package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.secret.LocalSecretCipher;
import com.songhg.veri.agent.common.secret.SecretProviderProperties;
import com.songhg.veri.agent.common.trace.TraceContext;
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
import com.songhg.veri.agent.management.application.ManagementWorkspaceService;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.ApplicationRef;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.DepartmentRef;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.EnvironmentConnectivityTargetRow;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.EnvironmentRef;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.IntegrationRow;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.ProjectRef;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.RoleRow;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.SecretProviderRow;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.SecretReferenceRow;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.SettingRow;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
public class PostgresManagementWorkspaceService implements ManagementWorkspaceService {

    private final ManagementMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogWriter auditLogWriter;
    private final PostgresManagementDeniedAuditRecorder deniedAuditRecorder;
    private final EnvironmentConnectivityChecker connectivityChecker;
    private final ObjectMapper objectMapper;
    private final SecretProviderProperties secretProviderProperties;

    public PostgresManagementWorkspaceService(
            ManagementMapper mapper,
            PasswordEncoder passwordEncoder,
            AuditLogWriter auditLogWriter,
            PostgresManagementDeniedAuditRecorder deniedAuditRecorder,
            EnvironmentConnectivityChecker connectivityChecker,
            ObjectMapper objectMapper,
            SecretProviderProperties secretProviderProperties
    ) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.auditLogWriter = auditLogWriter;
        this.deniedAuditRecorder = deniedAuditRecorder;
        this.connectivityChecker = connectivityChecker;
        this.objectMapper = objectMapper;
        this.secretProviderProperties = secretProviderProperties;
    }

    @Override
    public PageResponse<DepartmentView> departments(PageQuery pageQuery) {
        return page(mapper::listDepartments, mapper::countDepartments, pageQuery, values());
    }

    @Override
    @Transactional
    public DepartmentView createDepartment(String name, AuthUserPrincipal actor) {
        UUID deptId = UUID.randomUUID();
        String code = nextCode("dept");
        try {
            update(mapper::insertDepartment, actor, values(
                    "deptId", deptId,
                    "code", code,
                    "name", name,
                    "path", "/" + deptId
            ));
            insertDepartmentManager(deptId, actor);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "部门名称或编码已存在");
        }
        audit(actor, "创建部门", "department", deptId.toString(), name);
        return new DepartmentView(name, "总部", actor.displayName(), 0, "同步正常");
    }

    @Override
    public DepartmentView department(String key) {
        return departmentByKey(key);
    }

    @Override
    @Transactional
    public DepartmentView updateDepartment(String key, UpdateDepartmentRequest request, AuthUserPrincipal actor) {
        DepartmentRef department = resolveDepartmentStrict(key);
        ensureEnabled(department.status(), "当前部门状态不允许编辑");
        DepartmentView before = departmentByKey(department.id().toString());
        try {
            update(mapper::updateDepartment, actor, values(
                    "deptId", department.id(),
                    "name", blankToNull(request.name())
            ));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "部门名称已存在");
        }
        DepartmentView updated = departmentByKey(department.id().toString());
        auditChange(actor, "更新部门", "department", department.id().toString(), updated.name(),
                nameJson(before.name()), nameJson(updated.name()), null);
        return updated;
    }

    @Override
    @Transactional
    public DepartmentView changeDepartmentStatus(String key, String status, AuthUserPrincipal actor) {
        DepartmentRef department = resolveDepartmentStrict(key);
        String nextStatus = normalizeEnabledStatus(status, "部门状态不支持");
        update(mapper::changeDepartmentStatus, actor, values("deptId", department.id(), "status", nextStatus));
        DepartmentView updated = departmentByKey(department.id().toString());
        audit(actor, "ENABLED".equals(nextStatus) ? "启用部门" : "停用部门", "department", department.id().toString(), updated.name());
        return updated;
    }

    @Override
    public PageResponse<UserView> users(PageQuery pageQuery) {
        return page(mapper::listUsers, mapper::countUsers, pageQuery, values());
    }

    @Override
    public UserView user(String username) {
        return userByUsername(username);
    }

    @Override
    @Transactional
    public UserView createUser(String username, AuthUserPrincipal actor) {
        UUID userId = UUID.randomUUID();
        try {
            update(mapper::insertUser, actor, values("userId", userId, "username", username));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户账号已存在");
        }
        bindRoleIfPresent(userId, "Tester", "PLATFORM", null, actor);
        audit(actor, "邀请用户", "user", userId.toString(), username);
        return new UserView(username, username, "", "Tester", "未分配", "待激活", "尚未登录");
    }

    @Override
    @Transactional
    public UserView updateUser(String username, UpdateUserRequest request, AuthUserPrincipal actor) {
        UserView before = userByUsername(username);
        try {
            int rows = update(mapper::updateUser, actor, values(
                    "username", username,
                    "displayName", blankToNull(request.displayName()),
                    "email", blankToNull(request.email())
            ));
            ensureUserUpdated(rows);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户邮箱已存在");
        }
        UserView updated = userByUsername(username);
        auditChange(actor, "更新用户", "user", username, updated.username(),
                nameJson(before.displayName()), nameJson(updated.displayName()), null);
        return updated;
    }

    @Override
    @Transactional
    public UserView enableUser(String username, AuthUserPrincipal actor) {
        ensureUserUpdated(update(mapper::enableUser, actor, values("username", username)));
        audit(actor, "启用用户", "user", username, username);
        return userByUsername(username);
    }

    @Override
    @Transactional
    public UserView disableUser(String username, AuthUserPrincipal actor) {
        if (actor.username().equals(username)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "不能停用当前登录账号");
        }
        ensureUserUpdated(update(mapper::disableUser, actor, values("username", username)));
        audit(actor, "停用用户", "user", username, username);
        return userByUsername(username);
    }

    @Override
    @Transactional
    public UserView lockUser(String username, AuthUserPrincipal actor) {
        if (actor.username().equals(username)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "不能锁定当前登录账号");
        }
        ensureUserUpdated(update(mapper::lockUser, actor, values("username", username)));
        audit(actor, "锁定用户", "user", username, username);
        return userByUsername(username);
    }

    @Override
    @Transactional
    public UserView unlockUser(String username, AuthUserPrincipal actor) {
        ensureUserUpdated(update(mapper::unlockUser, actor, values("username", username)));
        audit(actor, "解锁用户", "user", username, username);
        return userByUsername(username);
    }

    @Override
    @Transactional
    public UserView resetUserPassword(String username, String newPassword, AuthUserPrincipal actor) {
        ensureUserUpdated(update(mapper::resetUserPassword, actor, values(
                "username", username,
                "passwordHash", passwordEncoder.encode(newPassword)
        )));
        audit(actor, "重置密码", "user", username, username);
        return userByUsername(username);
    }

    @Override
    public PageResponse<RoleView> roles(PageQuery pageQuery) {
        return page(mapper::listRoles, mapper::countRoles, pageQuery, values());
    }

    @Override
    public PageResponse<PermissionView> permissions(PageQuery pageQuery) {
        return page(mapper::listPermissions, mapper::countPermissions, pageQuery, values());
    }

    @Override
    public RoleDetailView role(String code) {
        return roleDetail(requireRoleRow(code));
    }

    @Override
    @Transactional
    public RoleDetailView createRole(CreateRoleRequest request, Set<String> assignablePermissions, AuthUserPrincipal actor) {
        String code = request.code().trim();
        String name = request.name().trim();
        String scopeType = request.scopeType().trim();
        String description = blankToNull(request.description());
        List<String> permissionCodes = normalizePermissionCodes(request.permissionCodes());
        ensureAssignablePermissions(permissionCodes, assignablePermissions);
        ensureEnabledPermissions(permissionCodes);
        UUID roleId = UUID.randomUUID();
        try {
            update(mapper::insertRole, actor, values(
                    "roleId", roleId,
                    "code", code,
                    "name", name,
                    "scopeType", scopeType,
                    "description", description
            ));
            replaceRolePermissions(roleId, permissionCodes, actor);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "角色编码已存在");
        }
        RoleDetailView created = roleDetail(requireRoleRow(code));
        audit(actor, "创建角色", "rbac_role", roleId.toString(), code);
        return created;
    }

    @Override
    @Transactional
    public RoleDetailView updateRole(String code, UpdateRoleRequest request, Set<String> assignablePermissions, AuthUserPrincipal actor) {
        RoleRow role = requireRoleRow(code);
        ensureCustomRole(role);
        RoleDetailView before = roleDetail(role);
        String name = blankToNull(request.name());
        String scopeType = blankToNull(request.scopeType());
        String description = request.description() == null ? null : request.description().trim();
        List<String> permissionCodes = null;
        if (request.permissionCodes() != null) {
            permissionCodes = normalizePermissionCodes(request.permissionCodes());
            ensureAssignablePermissions(permissionCodes, assignablePermissions);
            ensureEnabledPermissions(permissionCodes);
        }
        update(mapper::updateRole, actor, values(
                "roleId", role.id(),
                "name", name,
                "scopeType", scopeType,
                "description", description
        ));
        if (permissionCodes != null) {
            replaceRolePermissions(role.id(), permissionCodes, actor);
        }
        bumpUsersAuthVersionByRole(role.id(), actor);
        RoleDetailView updated = roleDetail(requireRoleRow(code));
        auditChange(actor, "更新角色", "rbac_role", role.id().toString(), updated.code(),
                roleJson(before), roleJson(updated), null);
        return updated;
    }

    @Override
    @Transactional
    public RoleDetailView changeRoleStatus(String code, String status, AuthUserPrincipal actor) {
        RoleRow role = requireRoleRow(code);
        ensureCustomRole(role);
        String nextStatus = normalizeEnabledStatus(status, "角色状态只支持 ENABLED 或 DISABLED");
        update(mapper::changeRoleStatus, actor, values("roleId", role.id(), "status", nextStatus));
        bumpUsersAuthVersionByRole(role.id(), actor);
        RoleDetailView updated = roleDetail(requireRoleRow(code));
        audit(actor, "ENABLED".equals(nextStatus) ? "启用角色" : "停用角色", "rbac_role", role.id().toString(), updated.code());
        return updated;
    }

    @Override
    @Transactional
    public UserView assignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        UUID userId = requireUserId(username);
        UUID roleId = requireRoleId(roleCode);
        update(mapper::assignUserRole, actor, values("userId", userId, "roleId", roleId, "roleCode", roleCode));
        bumpUserAuthVersion(userId, actor);
        audit(actor, "分配角色", "rbac_role_binding", userId + ":" + roleCode, username + ":" + roleCode);
        return userByUsername(username);
    }

    @Override
    @Transactional
    public UserView unassignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        UUID userId = requireUserId(username);
        int rows = update(mapper::unassignUserRole, actor, values("userId", userId, "roleCode", roleCode));
        if (rows > 0) {
            bumpUserAuthVersion(userId, actor);
        }
        audit(actor, "解绑角色", "rbac_role_binding", userId + ":" + roleCode, username + ":" + roleCode);
        return userByUsername(username);
    }

    @Override
    public PageResponse<ProjectView> projects(PageQuery pageQuery, AuthUserPrincipal actor) {
        return page(mapper::listProjects, mapper::countProjects, pageQuery, scope(actor));
    }

    @Override
    public ProjectView project(String key) {
        return projectByKey(key);
    }

    @Override
    @Transactional
    public ProjectView createProject(CreateProjectRequest request, AuthUserPrincipal actor) {
        UUID projectId = UUID.randomUUID();
        String name = request.name().trim();
        String code = normalizedOrGeneratedCode(request.code(), "prj");
        String sensitivityLevel = normalizedOrDefault(request.sensitivityLevel(), "INTERNAL");
        boolean allowPublicModel = Boolean.TRUE.equals(request.allowPublicModel());
        try {
            update(mapper::insertProject, actor, values(
                    "projectId", projectId,
                    "code", code,
                    "name", name,
                    "sensitivityLevel", sensitivityLevel,
                    "allowPublicModel", allowPublicModel
            ));
            insertProjectOwner(projectId, actor);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "项目编码已存在");
        }
        audit(actor, "创建项目", "project", projectId.toString(), name);
        return new ProjectView(name, "未分配", actor.displayName(), 0, "规划中");
    }

    @Override
    @Transactional
    public ProjectView updateProject(String key, UpdateProjectRequest request, AuthUserPrincipal actor) {
        ProjectRef project = resolveProjectStrict(key);
        ensureProjectEditable(project.status());
        ProjectView before = projectByKey(project.id().toString());
        try {
            update(mapper::updateProject, actor, values(
                    "projectId", project.id(),
                    "name", blankToNull(request.name()),
                    "sensitivityLevel", blankToNull(request.sensitivityLevel()),
                    "allowPublicModel", request.allowPublicModel()
            ));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "项目编码或名称已存在");
        }
        ProjectView updated = projectByKey(project.id().toString());
        auditChange(actor, "更新项目", "project", project.id().toString(), updated.name(),
                nameJson(before.name()), nameJson(updated.name()), null);
        return updated;
    }

    @Override
    @Transactional
    public ProjectView changeProjectStatus(String key, String status, AuthUserPrincipal actor) {
        ProjectRef project = resolveProjectStrict(key);
        String nextStatus = normalizeProjectStatus(status);
        ensureProjectStatusTransition(actor, project, nextStatus);
        update(mapper::changeProjectStatus, actor, values("projectId", project.id(), "status", nextStatus));
        ProjectView updated = projectByKey(project.id().toString());
        audit(actor, projectStatusAction(nextStatus), "project", project.id().toString(), updated.name());
        return updated;
    }

    @Override
    public PageResponse<ProjectMemberView> projectMembers(String projectKey, PageQuery pageQuery) {
        ProjectRef project = resolveProjectStrict(projectKey);
        return page(mapper::listProjectMembers, mapper::countProjectMembers, pageQuery, values("projectId", project.id()));
    }

    @Override
    @Transactional
    public ProjectMemberView addProjectMember(String projectKey, ProjectMemberRequest request, AuthUserPrincipal actor) {
        ProjectRef project = resolveProjectStrict(projectKey);
        ensureProjectEditable(project.status());
        String username = request.username().trim();
        String roleCode = request.roleCode().trim();
        UUID userId = requireUserId(username);
        UUID roleId = requireRoleId(roleCode);
        String memberType = memberTypeForRole(roleCode);
        update(mapper::upsertProjectMember, actor, values("projectId", project.id(), "userId", userId, "memberType", memberType));
        bindProjectRole(userId, roleId, roleCode, project.id(), actor);
        bumpUserAuthVersion(userId, actor);
        ProjectMemberView view = projectMemberByUsername(project.id(), username);
        audit(actor, "添加项目成员", "project_member", project.id() + ":" + userId, project.name() + ":" + username);
        return view;
    }

    @Override
    @Transactional
    public ProjectMemberView removeProjectMember(String projectKey, String username, AuthUserPrincipal actor) {
        ProjectRef project = resolveProjectStrict(projectKey);
        UUID userId = requireUserId(username);
        ProjectMemberView current = projectMemberByUsername(project.id(), username);
        int rows = update(mapper::deleteProjectMember, actor, values("projectId", project.id(), "userId", userId));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目成员不存在");
        }
        update(mapper::disableProjectRoleBindings, actor, values("projectId", project.id(), "userId", userId));
        bumpUserAuthVersion(userId, actor);
        audit(actor, "移除项目成员", "project_member", project.id() + ":" + userId, project.name() + ":" + username);
        return new ProjectMemberView(current.username(), current.displayName(), current.role(), current.memberType(), "已移除");
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
        return page(mapper::listEnvironments, mapper::countEnvironments, pageQuery, scope(actor));
    }

    @Override
    public EnvironmentView environment(String key) {
        return environmentByKey(key);
    }

    @Override
    @Transactional
    public EnvironmentView createEnvironment(CreateEnvironmentRequest request, AuthUserPrincipal actor) {
        String name = request.name().trim();
        ProjectRef project = resolveProject(request.project(), actor);
        ensureProjectEditable(project.status());
        String scopeType = normalizedOrDefault(request.scopeType(), blankToNull(request.application()) == null ? "PROJECT" : "APPLICATION");
        String envType = normalizedOrDefault(request.envType(), "TEST");
        UUID appId = resolveEnvironmentApplicationId(request, project, scopeType);
        UUID envId = UUID.randomUUID();
        String code = normalizedOrGeneratedCode(request.code(), "env");
        String endpoint = normalizedOrDefault(request.apiBaseUrl(), code + ".local");
        try {
            update(mapper::insertEnvironment, actor, values(
                    "envId", envId,
                    "projectId", project.id(),
                    "appId", appId,
                    "scopeType", scopeType,
                    "code", code,
                    "name", name,
                    "envType", envType,
                    "webUrl", blankToNull(request.webUrl()),
                    "endpoint", endpoint
            ));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "环境编码已存在");
        }
        audit(actor, "新增环境", "environment", envId.toString(), name);
        return new EnvironmentView(name, project.name(), endpoint, "可用");
    }

    @Override
    @Transactional
    public EnvironmentView updateEnvironment(String key, UpdateEnvironmentRequest request, AuthUserPrincipal actor) {
        EnvironmentRef environment = resolveEnvironmentStrict(key);
        ensureEnabled(environment.status(), "当前环境状态不允许编辑");
        EnvironmentView before = environmentByKey(environment.id().toString());
        try {
            update(mapper::updateEnvironment, actor, values(
                    "environmentId", environment.id(),
                    "name", blankToNull(request.name()),
                    "envType", blankToNull(request.envType()),
                    "webUrl", blankToNull(request.webUrl()),
                    "apiBaseUrl", blankToNull(request.apiBaseUrl())
            ));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "环境编码或名称已存在");
        }
        EnvironmentView updated = environmentByKey(environment.id().toString());
        auditChange(actor, "更新环境", "environment", environment.id().toString(), updated.name(),
                nameJson(before.name()), nameJson(updated.name()), null);
        return updated;
    }

    @Override
    @Transactional
    public EnvironmentView changeEnvironmentStatus(String key, String status, AuthUserPrincipal actor) {
        EnvironmentRef environment = resolveEnvironmentStrict(key);
        String nextStatus = normalizeEnabledStatus(status, "环境状态不支持");
        update(mapper::changeEnvironmentStatus, actor, values("environmentId", environment.id(), "status", nextStatus));
        EnvironmentView updated = environmentByKey(environment.id().toString());
        audit(actor, "ENABLED".equals(nextStatus) ? "启用环境" : "停用环境", "environment", environment.id().toString(), updated.name());
        return updated;
    }

    @Override
    public EnvironmentConnectivityCheckView environmentConnectivityCheck(String key) {
        EnvironmentConnectivityTargetRow target = resolveEnvironmentConnectivityTarget(key);
        return environmentConnectivityCheckView(target);
    }

    @Override
    @Transactional
    public EnvironmentConnectivityCheckView checkEnvironmentConnectivity(String key, AuthUserPrincipal actor) {
        EnvironmentConnectivityTargetRow target = resolveEnvironmentConnectivityTarget(key);
        ensureEnabled(target.status(), "停用环境不可执行连通性检查");
        EnvironmentConnectivityCheckView result = connectivityChecker.check(
                target.name(),
                target.webUrl(),
                target.apiBaseUrl()
        );
        update(mapper::updateEnvironmentHealthCheck, actor, values(
                "environmentId", target.id(),
                "healthCheckJson", environmentConnectivityCheckJson(result)
        ));
        audit(actor, "环境连通性检查", "environment", target.id().toString(), target.name());
        return result;
    }

    @Override
    public PageResponse<ScopedUserRoleView> environmentUsers(String environmentKey, PageQuery pageQuery) {
        EnvironmentRef environment = resolveEnvironmentStrict(environmentKey);
        return scopedUserRoles(environment.id(), "ENVIRONMENT", "", pageQuery);
    }

    @Override
    @Transactional
    public ScopedUserRoleView addEnvironmentUser(String environmentKey, ScopedUserRoleRequest request, AuthUserPrincipal actor) {
        EnvironmentRef environment = resolveEnvironmentStrict(environmentKey);
        ensureEnabled(environment.status(), "当前环境状态不允许维护授权用户");
        String roleCode = request.roleCode().trim();
        if (!List.of("Tester", "Developer", "Auditor").contains(roleCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "环境授权用户只能绑定 Tester、Developer 或 Auditor 角色");
        }
        String username = request.username().trim();
        UUID userId = requireUserId(username);
        UUID roleId = requireRoleId(roleCode);
        bindScopedRole(userId, roleId, roleCode, "ENVIRONMENT", environment.id(), actor);
        bumpUserAuthVersion(userId, actor);
        ScopedUserRoleView view = scopedUserRoleByUsername(environment.id(), "ENVIRONMENT", "", username, "环境授权用户不存在");
        audit(actor, "添加环境授权", "environment_user", environment.id() + ":" + userId, environment.name() + ":" + username);
        return view;
    }

    @Override
    @Transactional
    public ScopedUserRoleView removeEnvironmentUser(String environmentKey, String username, AuthUserPrincipal actor) {
        EnvironmentRef environment = resolveEnvironmentStrict(environmentKey);
        ScopedUserRoleView current = scopedUserRoleByUsername(environment.id(), "ENVIRONMENT", "", username, "环境授权用户不存在");
        UUID userId = requireUserId(username);
        disableScopedRoles(userId, "ENVIRONMENT", environment.id(), "", actor);
        bumpUserAuthVersion(userId, actor);
        audit(actor, "移除环境授权", "environment_user", environment.id() + ":" + userId, environment.name() + ":" + username);
        return new ScopedUserRoleView(current.username(), current.displayName(), current.role(), current.scopeType(), "已移除");
    }

    @Override
    public PageResponse<IntegrationView> integrations(PageQuery pageQuery) {
        return page(mapper::listIntegrations, mapper::countIntegrations, pageQuery, values());
    }

    @Override
    public IntegrationView integration(String key) {
        return integrationView(integrationRow(key));
    }

    @Override
    @Transactional
    public IntegrationView createIntegration(CreateIntegrationRequest request, AuthUserPrincipal actor) {
        String key = integrationKey(request.code());
        if (key.isBlank()) {
            key = nextCode("integration");
        }
        String name = defaultText(request.name(), key);
        String category = defaultText(request.category(), "未分类");
        String scope = defaultText(request.scope(), "平台级");
        String configKey = integrationConfigKey(key);
        try {
            update(mapper::insertConfig, actor, values(
                    "scopeType", "SYSTEM",
                    "configKey", configKey,
                    "valueJson", integrationJson(name, category, scope)
            ));
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "集成配置已存在");
        }
        IntegrationView created = integrationView(integrationRow(key));
        audit(actor, "登记集成", "integration", configKey, created.name());
        return created;
    }

    @Override
    @Transactional
    public IntegrationView updateIntegration(String key, UpdateIntegrationRequest request, AuthUserPrincipal actor) {
        IntegrationRow current = integrationRow(key);
        String name = defaultText(request.name(), current.name());
        String category = defaultText(request.category(), current.category());
        String scope = defaultText(request.scope(), current.scope());
        update(mapper::updateIntegration, actor, values(
                "configKey", current.configKey(),
                "valueJson", integrationJson(name, category, scope)
        ));
        IntegrationView updated = integrationView(integrationRow(current.key()));
        audit(actor, "更新集成", "integration", current.configKey(), updated.name());
        return updated;
    }

    @Override
    @Transactional
    public IntegrationView changeIntegrationStatus(String key, String status, AuthUserPrincipal actor) {
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "集成配置状态只支持 ENABLED 或 DISABLED");
        }
        IntegrationRow current = integrationRow(key);
        update(mapper::changeConfigStatus, actor, values("configKey", current.configKey(), "status", status));
        IntegrationView updated = integrationView(integrationRow(current.key()));
        audit(actor, "ENABLED".equals(status) ? "启用集成" : "停用集成", "integration", current.configKey(), updated.name());
        return updated;
    }

    @Override
    public PageResponse<AuditLogView> auditLogs(PageQuery pageQuery, AuditLogQuery query, AuthUserPrincipal actor) {
        Map<String, Object> params = auditParams(pageQuery, query, actor);
        List<AuditLogView> items = mapper.listAuditLogs(params);
        long total = mapper.countAuditLogs(params);
        return PageResponse.of(items, pageQuery.index(), pageQuery.size(), total);
    }

    @Override
    public String exportAuditLogsCsv(AuditLogQuery query, AuthUserPrincipal actor) {
        PageQuery exportPage = PageQuery.of(0, 100);
        PageResponse<AuditLogView> page = auditLogs(exportPage, query, actor);
        StringBuilder csv = new StringBuilder("time,actor,action,target,result\n");
        page.items().forEach(item -> {
            appendCsvValue(csv, item.time());
            appendCsvValue(csv, item.actor());
            appendCsvValue(csv, item.action());
            appendCsvValue(csv, item.target());
            appendCsvValue(csv, item.result());
            csv.setLength(csv.length() - 1);
            csv.append('\n');
        });
        audit(actor, "导出审计", "audit_log", "audit_export", "审计日志导出");
        return csv.toString();
    }

    @Override
    public PageResponse<AuditOutboxView> auditOutbox(PageQuery pageQuery, AuditOutboxQuery query, AuthUserPrincipal actor) {
        Map<String, Object> params = auditOutboxParams(pageQuery, query);
        List<AuditOutboxView> items = mapper.listAuditOutbox(params);
        long total = mapper.countAuditOutbox(params);
        return PageResponse.of(items, pageQuery.index(), pageQuery.size(), total);
    }

    @Override
    public PageResponse<SettingView> settings(PageQuery pageQuery) {
        PageResponse<SettingRow> rows = page(mapper::listSettings, mapper::countSettings, pageQuery, values());
        return PageResponse.of(rows.items().stream().map(this::settingView).toList(), pageQuery.index(), pageQuery.size(), rows.total());
    }

    @Override
    public SettingView setting(String key) {
        return settingView(settingRow(key));
    }

    @Override
    @Transactional
    public SettingView createSetting(CreateSettingRequest request, AuthUserPrincipal actor) {
        String key = normalizeSearch(request.key());
        rejectSensitivePlainSetting(key, request.value());
        String scopeType = defaultText(request.scopeType(), "SYSTEM");
        String name = defaultText(request.name(), settingName(key));
        try {
            update(mapper::insertConfig, actor, values(
                    "scopeType", scopeType,
                    "configKey", key,
                    "valueJson", settingJson(name, request.value().trim())
            ));
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统设置已存在");
        }
        SettingView created = settingView(settingRow(key));
        audit(actor, "创建设置", "config", key, created.name());
        return created;
    }

    @Override
    @Transactional
    public SettingView updateSetting(String key, UpdateSettingRequest request, AuthUserPrincipal actor) {
        SettingRow current = settingRow(key);
        SettingView currentView = settingView(current);
        String name = defaultText(request.name(), currentView.name());
        String value = defaultText(request.value(), currentView.value());
        rejectSensitivePlainSetting(currentView.key(), value);
        String scopeType = defaultText(request.scopeType(), current.scopeType());
        update(mapper::updateSetting, actor, values(
                "scopeType", scopeType,
                "configKey", current.configKey(),
                "valueJson", settingJson(name, value)
        ));
        SettingView updated = settingView(settingRow(current.configKey()));
        audit(actor, "更新设置", "config", current.configKey(), updated.name());
        return updated;
    }

    @Override
    @Transactional
    public SettingView changeSettingStatus(String key, String status, AuthUserPrincipal actor) {
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "系统设置状态只支持 ENABLED 或 DISABLED");
        }
        SettingRow current = settingRow(key);
        update(mapper::changeConfigStatus, actor, values("status", status, "configKey", current.configKey()));
        SettingView updated = settingView(settingRow(current.configKey()));
        audit(actor, "ENABLED".equals(status) ? "启用设置" : "停用设置", "config", current.configKey(), updated.name());
        return updated;
    }

    @Override
    public PageResponse<SecretReferenceView> secrets(PageQuery pageQuery) {
        return page(mapper::listSecretReferences, mapper::countSecretReferences, pageQuery, values());
    }

    @Override
    @Transactional
    public SecretReferenceView createSecret(CreateSecretReferenceRequest request, AuthUserPrincipal actor) {
        String secretRef = request.secretRef().trim();
        String providerCode = defaultText(request.providerCode(), "");
        SecretProviderRow provider = requireOne(
                mapper::findSecretProviderForManage,
                values("providerCode", providerCode),
                "密钥提供方不存在"
        );
        ensureLocalProvider(provider);
        UUID secretRefId = UUID.randomUUID();
        LocalSecretCipher.EncryptedMaterial material = LocalSecretCipher.encrypt(request.value(), secretProviderProperties);
        String secretVersion = defaultText(request.secretVersion(), "v1");
        try {
            update(mapper::insertSecretReference, actor, values(
                    "secretRefId", secretRefId,
                    "providerId", provider.id(),
                    "secretRef", secretRef,
                    "scopeType", request.scopeType().trim(),
                    "scopeId", request.scopeId(),
                    "purpose", request.purpose().trim(),
                    "maskedValue", maskedSecret(),
                    "secretVersion", secretVersion,
                    "expiresAt", request.expiresAt()
            ));
            update(mapper::insertSecretLocalStore, actor, values(
                    "secretRefId", secretRefId,
                    "cipherText", material.cipherText(),
                    "iv", material.iv(),
                    "authTag", material.authTag(),
                    "algorithm", material.algorithm(),
                    "masterKeyVersion", material.masterKeyVersion()
            ));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "密钥引用已存在");
        }
        SecretReferenceView created = secretReferenceByRef(secretRef);
        audit(actor, "创建密钥引用", "secret_reference", created.id(), created.secretRef());
        return created;
    }

    @Override
    @Transactional
    public SecretReferenceView rotateSecret(RotateSecretReferenceRequest request, AuthUserPrincipal actor) {
        SecretReferenceRow current = secretReferenceRow(request.secretRef());
        ensureLocalProvider(current);
        if (!"ACTIVE".equals(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "只有 ACTIVE 密钥可轮换");
        }
        LocalSecretCipher.EncryptedMaterial material = LocalSecretCipher.encrypt(request.value(), secretProviderProperties);
        String nextVersion = defaultText(request.secretVersion(), nextSecretVersion(current.secretVersion()));
        update(mapper::updateSecretReferenceRotation, actor, values(
                "secretRefId", current.id(),
                "secretVersion", nextVersion,
                "maskedValue", maskedSecret(),
                "expiresAt", request.expiresAt()
        ));
        update(mapper::upsertSecretLocalStoreRotation, actor, values(
                "secretRefId", current.id(),
                "cipherText", material.cipherText(),
                "iv", material.iv(),
                "authTag", material.authTag(),
                "algorithm", material.algorithm(),
                "masterKeyVersion", material.masterKeyVersion()
        ));
        SecretReferenceView updated = secretReferenceByRef(current.secretRef());
        audit(actor, "轮换密钥引用", "secret_reference", updated.id(), updated.secretRef());
        return updated;
    }

    @Override
    @Transactional
    public SecretReferenceView disableSecret(DisableSecretReferenceRequest request, AuthUserPrincipal actor) {
        SecretReferenceRow current = secretReferenceRow(request.secretRef());
        update(mapper::revokeSecretReference, actor, values("secretRefId", current.id()));
        update(mapper::revokeSecretLocalStore, actor, values("secretRefId", current.id()));
        SecretReferenceView updated = secretReferenceByRef(current.secretRef());
        audit(actor, "撤销密钥引用", "secret_reference", updated.id(), updated.secretRef());
        return updated;
    }

    private void insertProjectOwner(UUID projectId, AuthUserPrincipal actor) {
        update(mapper::insertProjectOwner, actor, values("projectId", projectId));
    }

    private void insertDepartmentManager(UUID deptId, AuthUserPrincipal actor) {
        update(mapper::insertDepartmentManager, actor, values("deptId", deptId));
    }

    private UUID ensureDefaultProject(AuthUserPrincipal actor) {
        UUID existing = mapper.findDefaultProjectId(values());
        if (existing != null) {
            return existing;
        }
        UUID projectId = UUID.randomUUID();
        update(mapper::insertDefaultProject, actor, values("projectId", projectId));
        insertProjectOwner(projectId, actor);
        UUID created = mapper.findDefaultProjectId(values());
        return created == null ? projectId : created;
    }

    private ProjectRef resolveProject(String project, AuthUserPrincipal actor) {
        String keyword = blankToNull(project);
        if (keyword == null) {
            return new ProjectRef(ensureDefaultProject(actor), "默认项目", "ACTIVE");
        }
        return resolveProjectStrict(keyword);
    }

    private DepartmentRef resolveDepartmentStrict(String key) {
        return requireOne(mapper::findDepartmentRef, values("keyword", key), "部门不存在");
    }

    private ProjectRef resolveProjectStrict(String key) {
        return requireOne(mapper::findProjectRef, values("keyword", key), "项目不存在");
    }

    private ApplicationRef resolveApplicationStrict(String key) {
        return requireOne(mapper::findApplicationRef, values("keyword", key), "应用不存在");
    }

    private EnvironmentRef resolveEnvironmentStrict(String key) {
        return requireOne(mapper::findEnvironmentRef, values("keyword", key), "环境不存在");
    }

    private EnvironmentConnectivityTargetRow resolveEnvironmentConnectivityTarget(String key) {
        return requireOne(mapper::findEnvironmentConnectivityTarget, values("keyword", key), "环境不存在");
    }

    private DepartmentView departmentByKey(String key) {
        return requireOne(mapper::findDepartmentView, values("keyword", key), "部门不存在");
    }

    private ProjectView projectByKey(String key) {
        return requireOne(mapper::findProjectView, values("keyword", key), "项目不存在");
    }

    private ApplicationView applicationByKey(String key) {
        return requireOne(mapper::findApplicationView, values("keyword", key), "应用不存在");
    }

    private EnvironmentView environmentByKey(String key) {
        return requireOne(mapper::findEnvironmentView, values("keyword", key), "环境不存在");
    }

    private ProjectMemberView projectMemberByUsername(UUID projectId, String username) {
        return requireOne(mapper::findProjectMemberByUsername, values("projectId", projectId, "username", username), "项目成员不存在");
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

    private UUID resolveEnvironmentApplicationId(
            CreateEnvironmentRequest request,
            ProjectRef project,
            String scopeType
    ) {
        String application = blankToNull(request.application());
        if ("PROJECT".equals(scopeType)) {
            if (application != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目级环境不能绑定应用");
            }
            return null;
        }
        if (application == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应用级环境必须指定应用");
        }
        ApplicationRef app = requireOne(
                mapper::findApplicationRefInProject,
                values("projectId", project.id(), "application", application),
                "应用不存在"
        );
        ensureEnabled(app.status(), "当前应用状态不允许新增专属环境");
        return app.id();
    }

    private void bindRoleIfPresent(
            UUID userId,
            String roleCode,
            String scopeType,
            UUID scopeId,
            AuthUserPrincipal actor
    ) {
        UUID roleId = mapper.findRoleId(values("roleCode", roleCode));
        if (roleId == null) {
            return;
        }
        update(mapper::bindRoleIfPresent, actor, values(
                "userId", userId,
                "roleId", roleId,
                "roleCode", roleCode,
                "scopeType", scopeType,
                "scopeId", scopeId
        ));
    }

    private void bindProjectRole(
            UUID userId,
            UUID roleId,
            String roleCode,
            UUID projectId,
            AuthUserPrincipal actor
    ) {
        update(mapper::bindProjectRole, actor, values(
                "userId", userId,
                "roleId", roleId,
                "roleCode", roleCode,
                "projectId", projectId
        ));
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

    private void appendCsvValue(StringBuilder csv, Object value) {
        String raw = value == null ? "" : String.valueOf(value);
        String escaped = raw.replace("\"", "\"\"");
        csv.append('"').append(escaped).append('"').append(',');
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

    /**
     * Build a simple status-change JSON for before/after comparison.
     */
    private String statusJson(String status) {
        return "{\"status\":\"" + escapeJson(status) + "\"}";
    }

    private UserView userByUsername(String username) {
        return requireOne(mapper::findUserByUsername, values("username", username), "用户不存在");
    }

    private void ensureUserUpdated(int rows) {
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    private boolean hasPlatformScope(AuthUserPrincipal actor) {
        return actor.roles().stream().anyMatch(role -> List.of("SuperAdmin", "PlatformAdmin", "Auditor").contains(role));
    }

    private String normalizeSearch(String search) {
        return search == null ? "" : search.trim();
    }

    private String normalizeAuditResult(String result) {
        return switch (normalizeSearch(result).toUpperCase()) {
            case "成功", "SUCCESS" -> "SUCCESS";
            case "拒绝", "DENIED" -> "DENIED";
            case "失败", "FAILED" -> "FAILED";
            default -> normalizeSearch(result).toUpperCase();
        };
    }

    private IntegrationRow integrationRow(String key) {
        return requireOne(mapper::findIntegrationRow, values("key", normalizeSearch(key)), "集成配置不存在");
    }

    private SettingRow settingRow(String key) {
        return requireOne(mapper::findSettingRow, values("key", normalizeSearch(key)), "系统设置不存在");
    }

    private SecretReferenceRow secretReferenceRow(String secretRef) {
        return requireOne(mapper::findSecretReferenceRow, values("secretRef", normalizeSearch(secretRef)), "密钥引用不存在");
    }

    private SecretReferenceView secretReferenceByRef(String secretRef) {
        return requireOne(mapper::findSecretReferenceView, values("secretRef", normalizeSearch(secretRef)), "密钥引用不存在");
    }

    private void ensureLocalProvider(SecretProviderRow provider) {
        if (!"LOCAL_ENCRYPTED".equals(provider.providerType()) || !"ENABLED".equals(provider.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前密钥提供方不支持本地写入和轮换");
        }
    }

    private void ensureLocalProvider(SecretReferenceRow secret) {
        if (!"LOCAL_ENCRYPTED".equals(secret.providerType())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前密钥引用不支持本地轮换");
        }
    }

    private IntegrationView integrationView(IntegrationRow row) {
        return new IntegrationView(row.key(), row.name(), row.category(), row.scope(), row.status());
    }

    private SettingView settingView(SettingRow row) {
        return new SettingView(
                row.configKey(),
                defaultText(row.displayName(), settingName(row.configKey())),
                row.value(),
                scopeName(row.scopeType()),
                row.status()
        );
    }

    private EnvironmentConnectivityCheckView environmentConnectivityCheckView(EnvironmentConnectivityTargetRow row) {
        String raw = blankToNull(row.healthCheckJson());
        if (raw == null || "{}".equals(raw)) {
            return EnvironmentConnectivityCheckView.notChecked(row.name());
        }
        try {
            EnvironmentConnectivityCheckView view = objectMapper.readValue(raw, EnvironmentConnectivityCheckView.class);
            if (view.status() == null || view.status().isBlank()) {
                return EnvironmentConnectivityCheckView.notChecked(row.name());
            }
            return view;
        } catch (JsonProcessingException exception) {
            return new EnvironmentConnectivityCheckView(
                    row.name(),
                    "SKIPPED",
                    "",
                    null,
                    "历史连通性结果不可读",
                    TraceContext.getTraceId(),
                    List.of()
            );
        }
    }

    private String environmentConnectivityCheckJson(EnvironmentConnectivityCheckView view) {
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "环境连通性结果保存失败");
        }
    }

    private String integrationKey(String code) {
        return normalizeSearch(code).toLowerCase();
    }

    private String integrationConfigKey(String key) {
        return "integration." + key;
    }

    private String integrationJson(String name, String category, String scope) {
        return "{\"name\":\"" + escapeJson(name) + "\","
                + "\"category\":\"" + escapeJson(category) + "\","
                + "\"scope\":\"" + escapeJson(scope) + "\"}";
    }

    private String settingJson(String name, String value) {
        return "{\"_display_name\":\"" + escapeJson(name) + "\","
                + "\"_value\":\"" + escapeJson(value) + "\"}";
    }

    private String defaultText(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private String maskedSecret() {
        return "********";
    }

    private String nextSecretVersion(String currentVersion) {
        String normalized = defaultText(currentVersion, "v1");
        if (normalized.matches("v\\d+")) {
            int version = Integer.parseInt(normalized.substring(1));
            return "v" + (version + 1);
        }
        return normalized + "-rotated";
    }

    private RoleRow requireRoleRow(String code) {
        String roleCode = blankToNull(code);
        if (roleCode == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        return requireOne(mapper::findRoleRow, values("roleCode", roleCode), "角色不存在");
    }

    private RoleDetailView roleDetail(RoleRow row) {
        return new RoleDetailView(
                row.code(),
                row.name(),
                row.scopeType(),
                "ENABLED".equals(row.status()) ? "启用" : "已停用",
                row.description(),
                row.system(),
                row.builtin(),
                row.version(),
                mapper.listRolePermissionCodes(values("roleId", row.id()))
        );
    }

    private void ensureCustomRole(RoleRow row) {
        if (row.system() || row.builtin()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "内置角色不可编辑或停用");
        }
    }

    private List<String> normalizePermissionCodes(List<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "角色至少需要一个权限点");
        }
        Set<String> normalizedCodes = new LinkedHashSet<>();
        for (String permissionCode : permissionCodes) {
            String normalized = blankToNull(permissionCode);
            if (normalized == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "权限点编码不能为空");
            }
            normalizedCodes.add(normalized);
        }
        if (normalizedCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "角色至少需要一个权限点");
        }
        return new ArrayList<>(normalizedCodes);
    }

    private void ensureAssignablePermissions(List<String> permissionCodes, Set<String> assignablePermissions) {
        List<String> forbidden = permissionCodes.stream()
                .filter(permissionCode -> assignablePermissions == null || !assignablePermissions.contains(permissionCode))
                .toList();
        if (!forbidden.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能授予超过操作者自身权限的权限点: " + String.join(",", forbidden));
        }
    }

    private void ensureEnabledPermissions(List<String> permissionCodes) {
        Set<String> enabled = new LinkedHashSet<>(mapper.listEnabledPermissionCodes(values("permissionCodes", permissionCodes)));
        List<String> missing = permissionCodes.stream()
                .filter(permissionCode -> !enabled.contains(permissionCode))
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "权限点不存在或已停用: " + String.join(",", missing));
        }
    }

    private void replaceRolePermissions(UUID roleId, List<String> permissionCodes, AuthUserPrincipal actor) {
        update(mapper::softDeleteRolePermissions, actor, values("roleId", roleId));
        update(mapper::insertRolePermissions, actor, values("roleId", roleId, "permissionCodes", permissionCodes));
    }

    private void bumpUsersAuthVersionByRole(UUID roleId, AuthUserPrincipal actor) {
        update(mapper::bumpUsersAuthVersionByRole, actor, values("roleId", roleId));
    }

    private String roleJson(RoleDetailView role) {
        return "{\"code\":\"" + escapeJson(role.code()) + "\","
                + "\"name\":\"" + escapeJson(role.name()) + "\","
                + "\"scopeType\":\"" + escapeJson(role.scopeType()) + "\","
                + "\"status\":\"" + escapeJson(role.status()) + "\","
                + "\"permissionCodes\":" + stringArrayJson(role.permissionCodes()) + ","
                + "\"version\":" + role.version() + "}";
    }

    private String stringArrayJson(List<String> values) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append('"').append(escapeJson(values.get(index))).append('"');
        }
        return json.append(']').toString();
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

    private Map<String, Object> auditParams(PageQuery pageQuery, AuditLogQuery query, AuthUserPrincipal actor) {
        Map<String, Object> params = pageParams(pageQuery, scope(actor));
        params.put("actor", query.actor());
        params.put("action", query.action());
        params.put("resourceType", query.resourceType());
        params.put("result", normalizeAuditResult(query.result()));
        params.put("startTime", query.startTime());
        params.put("endTime", query.endTime());
        return params;
    }

    private Map<String, Object> auditOutboxParams(PageQuery pageQuery, AuditOutboxQuery query) {
        Map<String, Object> params = pageParams(pageQuery, values());
        params.put("status", query.status());
        params.put("traceId", query.traceId());
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

    private String normalizeProjectStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("PREPARING", "ACTIVE", "ARCHIVED", "DISABLED").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目状态不支持");
        }
        return normalized;
    }

    private String normalizeEnabledStatus(String status, String message) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("ENABLED", "DISABLED").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private void ensureProjectStatusTransition(AuthUserPrincipal actor, ProjectRef project, String nextStatus) {
        String currentStatus = project.status();
        if (currentStatus.equals(nextStatus)) {
            return;
        }
        boolean allowed = switch (currentStatus) {
            case "PREPARING" -> List.of("ACTIVE", "DISABLED").contains(nextStatus);
            case "ACTIVE" -> List.of("ARCHIVED", "DISABLED").contains(nextStatus);
            case "ARCHIVED" -> List.of("ACTIVE", "DISABLED").contains(nextStatus);
            case "DISABLED" -> List.of("PREPARING", "ACTIVE").contains(nextStatus);
            default -> false;
        };
        if (!allowed) {
            deniedAuditRecorder.recordProjectStatusDenied(actor, project.id(), project.name(), currentStatus, nextStatus);
            throw new BusinessException(ErrorCode.INVALID_STATE, "项目状态不允许从 " + currentStatus + " 流转到 " + nextStatus);
        }
    }

    private String projectStatusAction(String status) {
        return switch (status) {
            case "ARCHIVED" -> "归档项目";
            case "DISABLED" -> "停用项目";
            case "ACTIVE", "PREPARING" -> "恢复项目";
            default -> "更新项目状态";
        };
    }

    private String memberTypeForRole(String roleCode) {
        return "ProjectOwner".equals(roleCode) ? "OWNER" : "MEMBER";
    }

    private void rejectSensitivePlainSetting(String key, String value) {
        String normalizedKey = normalizeSearch(key).toLowerCase();
        String normalizedValue = normalizeSearch(value);
        boolean sensitiveKey = normalizedKey.matches(".*(password|passwd|pwd|secret|token|api[_.-]?key|cookie|credential|private[_.-]?key).*");
        if (sensitiveKey && !normalizedValue.matches("^(\\*+|已配置|secret-ref:.+|\\$\\{[A-Za-z0-9_]+})$")) {
            throw new BusinessException(ErrorCode.SECRET_POLICY_VIOLATION, "敏感配置必须使用密钥引用或掩码值");
        }
    }

    private String nextCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String settingName(String configKey) {
        return switch (configKey) {
            case "audit.retention_days" -> "审计日志保留";
            case "audit.retention_cleanup_enabled" -> "审计保留清理";
            case "audit.retention_min_days" -> "审计最小保留";
            case "audit.retention_cleanup_batch_size" -> "审计清理批量";
            case "session.access_token_ttl_minutes" -> "访问令牌有效期";
            case "allow_public_model" -> "允许公有云模型";
            case "sensitivity_level" -> "默认敏感级别";
            case "default_resource_pool" -> "默认资源池";
            case "secret.default_provider" -> "默认密钥提供方";
            default -> configKey;
        };
    }

    private String scopeName(String scopeType) {
        return switch (scopeType) {
            case "SYSTEM" -> "平台级";
            case "PROJECT" -> "项目级";
            case "APPLICATION" -> "应用级";
            case "ENVIRONMENT" -> "环境级";
            default -> scopeType;
        };
    }
}
