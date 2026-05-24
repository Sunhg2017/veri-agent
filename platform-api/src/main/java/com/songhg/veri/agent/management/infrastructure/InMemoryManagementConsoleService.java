package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.audit.InMemoryAuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("local")
@Service
public class InMemoryManagementConsoleService implements ManagementConsoleService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final List<IntegrationView> integrations = new ArrayList<>();
    private final List<RoleView> roles = new ArrayList<>();
    private final List<PermissionView> permissions = new ArrayList<>();
    private final Map<String, List<String>> rolePermissions = new HashMap<>();
    private final List<SettingView> settings = new ArrayList<>();
    private final List<SecretReferenceView> secrets = new ArrayList<>();
    private final List<AuditLogView> auditLogs = new ArrayList<>();
    private final List<AuditOutboxView> auditOutbox = new ArrayList<>();
    private final InMemoryManagementDepartmentService departmentService;
    private final InMemoryManagementUserService userService;
    private final InMemoryManagementProjectService projectService;
    private final InMemoryManagementApplicationService applicationService;
    private final InMemoryManagementEnvironmentService environmentService;
    private final AuditLogWriter auditLogWriter;

    public InMemoryManagementConsoleService(
            AuditLogWriter auditLogWriter,
            EnvironmentConnectivityChecker connectivityChecker
    ) {
        this.auditLogWriter = auditLogWriter;
        departmentService = new InMemoryManagementDepartmentService(auditLogWriter);
        userService = new InMemoryManagementUserService(auditLogWriter);
        projectService = new InMemoryManagementProjectService(userService, auditLogWriter);
        applicationService = new InMemoryManagementApplicationService(userService, auditLogWriter);
        environmentService = new InMemoryManagementEnvironmentService(userService, auditLogWriter, connectivityChecker);
        integrations.addAll(List.of(
                new IntegrationView("github-enterprise", "GitHub Enterprise", "代码仓库", "全局", "已启用"),
                new IntegrationView("jenkins", "Jenkins", "CI/CD", "平台级", "已启用"),
                new IntegrationView("feishu-bot", "Feishu Bot", "通知", "项目级", "已启用")
        ));
        roles.addAll(List.of(
                new RoleView("SuperAdmin", "超级管理员", "PLATFORM", "启用", "平台初始化、组织治理、平台审计"),
                new RoleView("PlatformAdmin", "平台管理员", "PLATFORM", "启用", "组织、用户、项目、应用、环境、权限、审计管理"),
                new RoleView("Tester", "测试工程师", "ENVIRONMENT", "启用", "授权范围只读和启用环境使用")
        ));
        seedPermissions();
        rolePermissions.put("SuperAdmin", permissionCodes());
        rolePermissions.put("PlatformAdmin", List.of(
                "department:read", "department:create", "department:edit", "department:enable", "department:disable",
                "department:member_manage", "user:read", "user:create", "user:edit", "user:enable", "user:disable",
                "user:lock", "user:unlock", "user:assign_role", "role:read", "role:bind", "role:unbind",
                "project:read", "project:create", "project:edit", "project:archive", "project:disable", "project:member_manage",
                "application:read", "application:create", "application:edit", "application:disable", "application:owner_manage",
                "environment:read", "environment:create", "environment:edit", "environment:disable", "environment:use",
                "environment:user_manage", "config:read", "config:edit", "audit:read", "audit:export",
                "secret:reference", "context:read", "context:switch", "context:effective_read"
        ));
        rolePermissions.put("Tester", List.of(
                "project:read", "application:read", "environment:read", "environment:use",
                "config:read", "context:read", "context:switch", "context:effective_read",
                "asset:read", "asset:manage", "asset:review",
                "requirementInput:read", "requirementInput:import", "requirementInput:candidate_review"
        ));
        settings.addAll(List.of(
                new SettingView("password.min_length", "密码最小长度", "10 位", "全局安全策略", "已启用"),
                new SettingView("audit.retention_days", "审计日志保留", "365 天", "合规策略", "已启用"),
                new SettingView("audit.retention_cleanup_enabled", "审计保留清理", "false", "合规策略", "已停用"),
                new SettingView("audit.retention_min_days", "审计最小保留", "30 天", "合规策略", "已启用"),
                new SettingView("project.default_status", "默认项目状态", "规划中", "项目开通", "已启用")
        ));
        auditLogs.addAll(List.of(
                new AuditLogView("2026-05-16 10:31", "system", "健康检查", "platform-api", "成功"),
                new AuditLogView("2026-05-16 09:48", "shao.min", "创建部门", "端体验组", "成功"),
                new AuditLogView("2026-05-15 18:12", "he.xu", "更新角色", "ProjectOwner", "成功")
        ));
        auditOutbox.addAll(List.of(
                new AuditOutboxView(
                        "8f57078c-4a7f-4b80-bf72-7ef03d252001",
                        "trc_outbox_pending",
                        "audit:pending:001",
                        "PENDING",
                        1,
                        "2026-05-21 10:05",
                        "",
                        "",
                        "",
                        "创建部门",
                        "department",
                        "dept-qa",
                        "SUCCESS",
                        "2026-05-21 10:00",
                        "2026-05-21 10:00"
                ),
                new AuditOutboxView(
                        "8f57078c-4a7f-4b80-bf72-7ef03d252002",
                        "trc_outbox_failed",
                        "audit:failed:001",
                        "FAILED",
                        4,
                        "2026-05-21 10:30",
                        "",
                        "wp1-audit-worker-1",
                        "insert audit_log timeout",
                        "重置密码",
                        "user",
                        "tester.lifecycle",
                        "SUCCESS",
                        "2026-05-21 09:45",
                        "2026-05-21 09:58"
                )
        ));
    }

    private void seedPermissions() {
        List.of(
                "role:read", "role:create", "role:edit", "role:bind", "role:unbind",
                "audit:read", "audit:export", "audit:write_internal",
                "context:read", "context:switch", "context:effective_read",
                "department:read", "department:create", "department:edit", "department:enable", "department:disable",
                "department:member_manage",
                "user:read", "user:create", "user:edit", "user:enable", "user:disable", "user:lock", "user:unlock",
                "user:assign_role", "user:reset_password",
                "project:read", "project:create", "project:edit", "project:archive", "project:disable", "project:member_manage",
                "application:read", "application:create", "application:edit", "application:disable", "application:owner_manage",
                "environment:read", "environment:create", "environment:edit", "environment:disable", "environment:use",
                "environment:user_manage",
                "config:read", "config:edit",
                "secret:reference", "secret:read", "secret:manage", "secret:rotate", "secret:disable",
                "asset:read", "asset:manage", "asset:review", "asset:export",
                "modelAccess:read", "modelAccess:manage", "modelAccess:export",
                "requirementInput:read", "requirementInput:manage", "requirementInput:import",
                "requirementInput:candidate_review", "requirementInput:publish", "requirementInput:webhook_replay"
        ).forEach(code -> permissions.add(permission(code)));
    }

    private PermissionView permission(String code) {
        String[] parts = code.split(":", 2);
        return new PermissionView(code, parts[0], parts.length == 2 ? parts[1] : "", "PLATFORM,PROJECT,APPLICATION,ENVIRONMENT", "", "启用");
    }

    private List<String> permissionCodes() {
        return permissions.stream().map(PermissionView::code).toList();
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
        return page(roles, pageQuery);
    }

    @Override
    public synchronized PageResponse<PermissionView> permissions(PageQuery pageQuery) {
        return page(permissions, pageQuery);
    }

    @Override
    public synchronized RoleDetailView role(String code) {
        RoleView role = requireRoleView(code);
        return roleDetail(role);
    }

    @Override
    public synchronized RoleDetailView createRole(CreateRoleRequest request, Set<String> assignablePermissions, AuthUserPrincipal actor) {
        String code = request.code().trim();
        if (roles.stream().anyMatch(role -> role.code().equals(code))) {
            throw new BusinessException(ErrorCode.CONFLICT, "角色编码已存在");
        }
        List<String> permissionCodes = normalizePermissionCodes(request.permissionCodes());
        ensureAssignablePermissions(permissionCodes, assignablePermissions);
        ensureKnownPermissions(permissionCodes);
        RoleView view = new RoleView(
                code,
                request.name().trim(),
                request.scopeType().trim(),
                "启用",
                defaultText(request.description(), "")
        );
        roles.add(view);
        rolePermissions.put(code, permissionCodes);
        audit(actor, "创建角色", code);
        return roleDetail(view);
    }

    @Override
    public synchronized RoleDetailView updateRole(String code, UpdateRoleRequest request, Set<String> assignablePermissions, AuthUserPrincipal actor) {
        RoleView current = requireRoleView(code);
        ensureCustomRole(current);
        List<String> nextPermissionCodes = rolePermissions.getOrDefault(current.code(), List.of());
        if (request.permissionCodes() != null) {
            nextPermissionCodes = normalizePermissionCodes(request.permissionCodes());
            ensureAssignablePermissions(nextPermissionCodes, assignablePermissions);
            ensureKnownPermissions(nextPermissionCodes);
        }
        RoleView updated = new RoleView(
                current.code(),
                trimOrDefault(request.name(), current.name()),
                trimOrDefault(request.scopeType(), current.scopeType()),
                current.status(),
                request.description() == null ? current.description() : request.description().trim()
        );
        replaceRole(updated);
        rolePermissions.put(updated.code(), nextPermissionCodes);
        audit(actor, "更新角色", updated.code());
        return roleDetail(updated);
    }

    @Override
    public synchronized RoleDetailView changeRoleStatus(String code, String status, AuthUserPrincipal actor) {
        RoleView current = requireRoleView(code);
        ensureCustomRole(current);
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "角色状态只支持 ENABLED 或 DISABLED");
        }
        RoleView updated = new RoleView(
                current.code(),
                current.name(),
                current.scopeType(),
                "ENABLED".equals(status) ? "启用" : "已停用",
                current.description()
        );
        replaceRole(updated);
        audit(actor, "ENABLED".equals(status) ? "启用角色" : "停用角色", updated.code());
        return roleDetail(updated);
    }

    @Override
    public synchronized UserView assignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        requireRole(roleCode);
        return userService.assignUserRole(username, roleCode, actor);
    }

    @Override
    public synchronized UserView unassignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        return userService.unassignUserRole(username, roleCode, actor);
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
        return page(integrations, pageQuery);
    }

    @Override
    public synchronized IntegrationView integration(String key) {
        return requireIntegration(key);
    }

    @Override
    public synchronized IntegrationView createIntegration(CreateIntegrationRequest request, AuthUserPrincipal actor) {
        String key = integrationKey(request.code(), request.name());
        if (integrations.stream().anyMatch(integration -> integration.key().equals(key))) {
            throw new BusinessException(ErrorCode.CONFLICT, "集成配置已存在");
        }
        IntegrationView view = new IntegrationView(
                key,
                request.name().trim(),
                defaultText(request.category(), "未分类"),
                defaultText(request.scope(), "平台级"),
                "已启用"
        );
        integrations.add(0, view);
        audit(actor, "登记集成", view.name());
        return view;
    }

    @Override
    public synchronized IntegrationView updateIntegration(String key, UpdateIntegrationRequest request, AuthUserPrincipal actor) {
        IntegrationView current = requireIntegration(key);
        IntegrationView updated = new IntegrationView(
                current.key(),
                defaultText(request.name(), current.name()),
                defaultText(request.category(), current.category()),
                defaultText(request.scope(), current.scope()),
                current.status()
        );
        integrations.removeIf(integration -> integration.key().equals(current.key()));
        integrations.add(0, updated);
        audit(actor, "更新集成", updated.name());
        return updated;
    }

    @Override
    public synchronized IntegrationView changeIntegrationStatus(String key, String status, AuthUserPrincipal actor) {
        IntegrationView current = requireIntegration(key);
        String nextStatus = "DISABLED".equals(status) ? "已停用" : "已启用";
        IntegrationView updated = new IntegrationView(current.key(), current.name(), current.category(), current.scope(), nextStatus);
        integrations.removeIf(integration -> integration.key().equals(current.key()));
        integrations.add(0, updated);
        audit(actor, "DISABLED".equals(status) ? "停用集成" : "启用集成", updated.name());
        return updated;
    }

    @Override
    public synchronized PageResponse<AuditLogView> auditLogs(PageQuery pageQuery, AuditLogQuery query, AuthUserPrincipal actor) {
        List<AuditLogView> combined = new ArrayList<>();
        combined.addAll(InMemoryAuditLogWriter.records().stream()
                .map(this::auditRecordView)
                .toList());
        combined.addAll(auditLogs);
        List<AuditLogView> filtered = combined.stream()
                .filter(item -> matchesAuditLog(item, query))
                .toList();
        return page(filtered, PageQuery.of(pageQuery.index(), pageQuery.size()));
    }

    @Override
    public synchronized String exportAuditLogsCsv(AuditLogQuery query, AuthUserPrincipal actor) {
        PageResponse<AuditLogView> page = auditLogs(PageQuery.of(0, 100), query, actor);
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
        audit(actor, "导出审计", "audit_log");
        return csv.toString();
    }

    @Override
    public synchronized PageResponse<AuditOutboxView> auditOutbox(
            PageQuery pageQuery,
            AuditOutboxQuery query,
            AuthUserPrincipal actor
    ) {
        List<AuditOutboxView> filtered = auditOutbox.stream()
                .filter(item -> matchesAuditOutbox(item, query))
                .toList();
        return page(filtered, PageQuery.of(pageQuery.index(), pageQuery.size()));
    }

    private AuditLogView auditRecordView(AuditLogWriter.AuditRecord record) {
        return new AuditLogView(
                LocalDateTime.now().format(TIME_FORMAT),
                record.actor() == null ? "system" : record.actor().username(),
                record.action(),
                record.targetName(),
                resultName(record.result())
        );
    }

    private void appendCsvValue(StringBuilder csv, Object value) {
        String raw = value == null ? "" : String.valueOf(value);
        String escaped = raw.replace("\"", "\"\"");
        csv.append('"').append(escaped).append('"').append(',');
    }

    @Override
    public synchronized PageResponse<SettingView> settings(PageQuery pageQuery) {
        return page(settings.stream()
                .filter(setting -> "已启用".equals(setting.status()))
                .toList(), pageQuery);
    }

    @Override
    public synchronized SettingView setting(String key) {
        return requireSetting(key);
    }

    @Override
    public synchronized SettingView createSetting(CreateSettingRequest request, AuthUserPrincipal actor) {
        String key = request.key().trim();
        rejectSensitivePlainSetting(key, request.value());
        if (settings.stream().anyMatch(setting -> setting.key().equals(key))) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统设置已存在");
        }
        SettingView view = new SettingView(
                key,
                defaultText(request.name(), key),
                request.value().trim(),
                settingScopeName(request.scopeType()),
                "已启用"
        );
        settings.add(0, view);
        audit(actor, "创建设置", view.name());
        return view;
    }

    @Override
    public synchronized SettingView updateSetting(String key, UpdateSettingRequest request, AuthUserPrincipal actor) {
        SettingView current = requireSetting(key);
        rejectSensitivePlainSetting(current.key(), defaultText(request.value(), current.value()));
        SettingView updated = new SettingView(
                current.key(),
                defaultText(request.name(), current.name()),
                defaultText(request.value(), current.value()),
                settingScopeName(request.scopeType(), current.scope()),
                current.status()
        );
        settings.removeIf(setting -> setting.key().equals(current.key()));
        settings.add(0, updated);
        audit(actor, "更新设置", updated.name());
        return updated;
    }

    @Override
    public synchronized SettingView changeSettingStatus(String key, String status, AuthUserPrincipal actor) {
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "系统设置状态只支持 ENABLED 或 DISABLED");
        }
        SettingView current = requireSetting(key);
        SettingView updated = new SettingView(
                current.key(),
                current.name(),
                current.value(),
                current.scope(),
                "ENABLED".equals(status) ? "已启用" : "已停用"
        );
        settings.removeIf(setting -> setting.key().equals(current.key()));
        settings.add(0, updated);
        audit(actor, "ENABLED".equals(status) ? "启用设置" : "停用设置", updated.name());
        return updated;
    }

    @Override
    public synchronized PageResponse<SecretReferenceView> secrets(PageQuery pageQuery) {
        return page(secrets, pageQuery);
    }

    @Override
    public synchronized SecretReferenceView createSecret(CreateSecretReferenceRequest request, AuthUserPrincipal actor) {
        String secretRef = request.secretRef().trim();
        if (secrets.stream().anyMatch(secret -> secret.secretRef().equals(secretRef))) {
            throw new BusinessException(ErrorCode.CONFLICT, "密钥引用已存在");
        }
        String now = LocalDateTime.now().format(TIME_FORMAT);
        SecretReferenceView view = new SecretReferenceView(
                UUID.randomUUID().toString(),
                secretRef,
                defaultText(request.providerCode(), "local"),
                "LOCAL_ENCRYPTED",
                request.purpose().trim(),
                request.scopeType().trim(),
                request.scopeId().toString(),
                maskedSecret(),
                defaultText(request.secretVersion(), "v1"),
                "ACTIVE",
                now,
                request.expiresAt() == null ? "" : request.expiresAt().toString(),
                now,
                now
        );
        secrets.add(0, view);
        audit(actor, "创建密钥引用", secretRef);
        return view;
    }

    @Override
    public synchronized SecretReferenceView rotateSecret(RotateSecretReferenceRequest request, AuthUserPrincipal actor) {
        SecretReferenceView current = requireSecret(request.secretRef());
        if ("REVOKED".equals(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "已撤销密钥不可轮换");
        }
        String now = LocalDateTime.now().format(TIME_FORMAT);
        SecretReferenceView updated = new SecretReferenceView(
                current.id(),
                current.secretRef(),
                current.providerCode(),
                current.providerType(),
                current.purpose(),
                current.scopeType(),
                current.scopeId(),
                maskedSecret(),
                defaultText(request.secretVersion(), nextSecretVersion(current.secretVersion())),
                "ACTIVE",
                now,
                request.expiresAt() == null ? current.expiresAt() : request.expiresAt().toString(),
                current.createdAt(),
                now
        );
        replaceSecret(updated);
        audit(actor, "轮换密钥引用", current.secretRef());
        return updated;
    }

    @Override
    public synchronized SecretReferenceView disableSecret(DisableSecretReferenceRequest request, AuthUserPrincipal actor) {
        SecretReferenceView current = requireSecret(request.secretRef());
        String now = LocalDateTime.now().format(TIME_FORMAT);
        SecretReferenceView updated = new SecretReferenceView(
                current.id(),
                current.secretRef(),
                current.providerCode(),
                current.providerType(),
                current.purpose(),
                current.scopeType(),
                current.scopeId(),
                current.maskedValue(),
                current.secretVersion(),
                "REVOKED",
                current.rotatedAt(),
                current.expiresAt(),
                current.createdAt(),
                now
        );
        replaceSecret(updated);
        audit(actor, "撤销密钥引用", current.secretRef());
        return updated;
    }

    private void audit(AuthUserPrincipal actor, String action, String target) {
        auditLogWriter.record(AuditLogWriter.success(
                actor, action, "management", target, target
        ));
    }

    private void auditDenied(AuthUserPrincipal actor, String action, String target, String reason) {
        auditLogWriter.record(AuditLogWriter.denied(
                actor, action, "management", target, target, reason
        ));
    }

    private IntegrationView requireIntegration(String key) {
        return integrations.stream()
                .filter(integration -> integration.key().equals(key) || integration.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "集成配置不存在"));
    }

    private SettingView requireSetting(String key) {
        return settings.stream()
                .filter(setting -> setting.key().equals(key) || setting.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "系统设置不存在"));
    }

    private SecretReferenceView requireSecret(String secretRef) {
        String normalized = defaultText(secretRef, "");
        return secrets.stream()
                .filter(secret -> secret.secretRef().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "密钥引用不存在"));
    }

    private String integrationKey(String code, String name) {
        String seed = defaultText(code, name);
        String normalized = seed.trim().toLowerCase().replaceAll("[^a-z0-9_-]+", "-").replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? "integration-" + Math.abs(seed.hashCode()) : normalized;
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

    private String settingScopeName(String scopeType) {
        return settingScopeName(scopeType, "平台级");
    }

    private String settingScopeName(String scopeType, String fallback) {
        return switch (defaultText(scopeType, "")) {
            case "SYSTEM" -> "平台级";
            case "PROJECT" -> "项目级";
            case "APPLICATION" -> "应用级";
            case "ENVIRONMENT" -> "环境级";
            default -> fallback;
        };
    }

    private void rejectSensitivePlainSetting(String key, String value) {
        String normalizedKey = defaultText(key, "").toLowerCase();
        String normalizedValue = defaultText(value, "");
        boolean sensitiveKey = normalizedKey.matches(".*(password|passwd|pwd|secret|token|api[_.-]?key|cookie|credential|private[_.-]?key).*");
        if (sensitiveKey && !normalizedValue.matches("^(\\*+|已配置|secret-ref:.+|\\$\\{[A-Za-z0-9_]+})$")) {
            throw new BusinessException(ErrorCode.SECRET_POLICY_VIOLATION, "敏感配置必须使用密钥引用或掩码值");
        }
    }

    private <T> PageResponse<T> page(List<T> source, PageQuery pageQuery) {
        String keyword = pageQuery.search().toLowerCase();
        List<T> filtered = source.stream()
                .filter(item -> keyword.isBlank() || item.toString().toLowerCase().contains(keyword))
                .toList();
        int from = Math.min(pageQuery.offset(), filtered.size());
        int to = Math.min(from + pageQuery.size(), filtered.size());
        return PageResponse.of(filtered.subList(from, to), pageQuery.index(), pageQuery.size(), filtered.size());
    }

    private boolean matchesAuditLog(AuditLogView item, AuditLogQuery query) {
        String keyword = query.search().toLowerCase();
        if (!keyword.isBlank() && !item.toString().toLowerCase().contains(keyword)) {
            return false;
        }
        if (!query.actor().isBlank() && !item.actor().equalsIgnoreCase(query.actor())) {
            return false;
        }
        if (!query.action().isBlank() && !item.action().equals(query.action())) {
            return false;
        }
        if (!query.resourceType().isBlank() && !displayResourceType(item.action()).equalsIgnoreCase(query.resourceType())) {
            return false;
        }
        if (!query.result().isBlank()
                && !item.result().equalsIgnoreCase(query.result())
                && !item.result().equals(resultName(query.result()))) {
            return false;
        }
        OffsetDateTime itemTime = parseDisplayTime(item.time());
        if (query.startTime() != null && itemTime.isBefore(query.startTime())) {
            return false;
        }
        return query.endTime() == null || !itemTime.isAfter(query.endTime());
    }

    private boolean matchesAuditOutbox(AuditOutboxView item, AuditOutboxQuery query) {
        String keyword = query.search().toLowerCase();
        if (!keyword.isBlank() && !item.toString().toLowerCase().contains(keyword)) {
            return false;
        }
        if (!query.status().isBlank() && !item.status().equals(query.status())) {
            return false;
        }
        return query.traceId().isBlank() || item.traceId().equals(query.traceId());
    }

    private OffsetDateTime parseDisplayTime(String value) {
        LocalDateTime localDateTime = LocalDateTime.parse(value, TIME_FORMAT);
        return localDateTime.atOffset(ZoneOffset.ofHours(8));
    }

    private String displayResourceType(String action) {
        return switch (action) {
            case "创建部门" -> "department";
            case "邀请用户", "启用用户", "停用用户", "锁定用户", "解锁用户", "重置密码" -> "user";
            case "创建项目" -> "project";
            case "登记应用" -> "application";
            case "新增环境", "环境连通性检查" -> "environment";
            case "分配角色", "解绑角色" -> "rbac_role_binding";
            case "创建角色", "更新角色", "启用角色", "停用角色" -> "rbac_role";
            case "创建密钥引用", "轮换密钥引用", "撤销密钥引用" -> "secret_reference";
            default -> "";
        };
    }

    private String resultName(String result) {
        return switch (result.toUpperCase()) {
            case "SUCCESS" -> "成功";
            case "DENIED" -> "拒绝";
            case "FAILED" -> "失败";
            default -> result;
        };
    }

    private void requireRole(String roleCode) {
        requireRoleView(roleCode);
    }

    private RoleView requireRoleView(String roleCode) {
        return roles.stream()
                .filter(role -> role.code().equals(roleCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "角色不存在"));
    }

    private RoleDetailView roleDetail(RoleView role) {
        return new RoleDetailView(
                role.code(),
                role.name(),
                role.scopeType(),
                role.status(),
                role.description(),
                isBuiltinRole(role.code()),
                isBuiltinRole(role.code()),
                0,
                rolePermissions.getOrDefault(role.code(), List.of())
        );
    }

    private void ensureCustomRole(RoleView role) {
        if (isBuiltinRole(role.code())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "内置角色不可编辑或停用");
        }
    }

    private boolean isBuiltinRole(String roleCode) {
        return List.of("SuperAdmin", "PlatformAdmin", "DepartmentManager", "ProjectOwner", "AppOwner", "Tester", "Developer", "Auditor")
                .contains(roleCode);
    }

    private List<String> normalizePermissionCodes(List<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "角色至少需要一个权限点");
        }
        Set<String> normalizedCodes = new LinkedHashSet<>();
        for (String permissionCode : permissionCodes) {
            String normalized = defaultText(permissionCode, "");
            if (normalized.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "权限点编码不能为空");
            }
            normalizedCodes.add(normalized);
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

    private void ensureKnownPermissions(List<String> permissionCodes) {
        Set<String> known = new LinkedHashSet<>(permissionCodes());
        List<String> missing = permissionCodes.stream()
                .filter(permissionCode -> !known.contains(permissionCode))
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "权限点不存在或已停用: " + String.join(",", missing));
        }
    }

    private void replaceRole(RoleView updated) {
        for (int index = 0; index < roles.size(); index++) {
            if (roles.get(index).code().equals(updated.code())) {
                roles.set(index, updated);
                return;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
    }

    private void replaceSecret(SecretReferenceView updated) {
        for (int index = 0; index < secrets.size(); index++) {
            SecretReferenceView current = secrets.get(index);
            if (current.secretRef().equals(updated.secretRef())) {
                secrets.set(index, updated);
                return;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "密钥引用不存在");
    }

    private String trimOrDefault(String value, String defaultValue) {
        if (value == null || value.trim().isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

}
