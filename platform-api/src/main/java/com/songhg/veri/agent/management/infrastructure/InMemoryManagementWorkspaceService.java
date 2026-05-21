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
import com.songhg.veri.agent.management.api.request.CreateApplicationRequest;
import com.songhg.veri.agent.management.api.request.CreateEnvironmentRequest;
import com.songhg.veri.agent.management.api.request.CreateIntegrationRequest;
import com.songhg.veri.agent.management.api.request.CreateProjectRequest;
import com.songhg.veri.agent.management.api.request.CreateSettingRequest;
import com.songhg.veri.agent.management.api.response.DepartmentView;
import com.songhg.veri.agent.management.api.response.EnvironmentView;
import com.songhg.veri.agent.management.api.response.IntegrationView;
import com.songhg.veri.agent.management.api.response.ProjectView;
import com.songhg.veri.agent.management.api.request.ProjectMemberRequest;
import com.songhg.veri.agent.management.api.response.ProjectMemberView;
import com.songhg.veri.agent.management.api.response.RoleView;
import com.songhg.veri.agent.management.api.request.ScopedUserRoleRequest;
import com.songhg.veri.agent.management.api.response.ScopedUserRoleView;
import com.songhg.veri.agent.management.api.response.SettingView;
import com.songhg.veri.agent.management.api.request.UpdateDepartmentRequest;
import com.songhg.veri.agent.management.api.request.UpdateApplicationRequest;
import com.songhg.veri.agent.management.api.request.UpdateEnvironmentRequest;
import com.songhg.veri.agent.management.api.request.UpdateIntegrationRequest;
import com.songhg.veri.agent.management.api.request.UpdateProjectRequest;
import com.songhg.veri.agent.management.api.request.UpdateSettingRequest;
import com.songhg.veri.agent.management.api.request.UpdateUserRequest;
import com.songhg.veri.agent.management.api.response.UserView;
import com.songhg.veri.agent.management.application.AuditLogQuery;
import com.songhg.veri.agent.management.application.ManagementWorkspaceService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("local")
@Service
public class InMemoryManagementWorkspaceService implements ManagementWorkspaceService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final List<DepartmentView> departments = new ArrayList<>();
    private final List<UserView> users = new ArrayList<>();
    private final List<ProjectView> projects = new ArrayList<>();
    private final List<ProjectMemberView> projectMembers = new ArrayList<>();
    private final List<ApplicationView> applications = new ArrayList<>();
    private final List<ScopedUserRoleView> applicationOwners = new ArrayList<>();
    private final List<EnvironmentView> environments = new ArrayList<>();
    private final List<ScopedUserRoleView> environmentUsers = new ArrayList<>();
    private final List<IntegrationView> integrations = new ArrayList<>();
    private final List<RoleView> roles = new ArrayList<>();
    private final List<SettingView> settings = new ArrayList<>();
    private final List<AuditLogView> auditLogs = new ArrayList<>();
    private final AuditLogWriter auditLogWriter;

    public InMemoryManagementWorkspaceService(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
        departments.addAll(List.of(
                new DepartmentView("质量工程中心", "总部", "邵敏", 68, "同步正常"),
                new DepartmentView("自动化平台组", "质量工程中心", "何序", 16, "同步正常"),
                new DepartmentView("业务验收组", "质量工程中心", "赵文", 23, "待确认")
        ));
        users.addAll(List.of(
                new UserView("shao.min", "邵敏", "shao.min@example.com", "PlatformAdmin", "质量工程中心", "启用", "今天 10:24"),
                new UserView("he.xu", "何序", "he.xu@example.com", "ProjectOwner", "自动化平台组", "启用", "今天 09:43"),
                new UserView("zhao.wen", "赵文", "zhao.wen@example.com", "Auditor", "业务验收组", "待激活", "尚未登录")
        ));
        projects.addAll(List.of(
                new ProjectView("Checkout Regression", "自动化平台组", "何序", 4, "进行中"),
                new ProjectView("Mobile Smoke", "端体验组", "陈乔", 2, "规划中"),
                new ProjectView("API Stability", "质量工程中心", "平台组", 6, "进行中")
        ));
        applications.addAll(List.of(
                new ApplicationView("veri-agent-api", "Backend", "平台组", "v0.3.2", "已接入"),
                new ApplicationView("portal-web", "Frontend", "平台组", "v0.1.0", "接入中"),
                new ApplicationView("mobile-client", "Mobile", "端体验组", "v2.8.1", "待接入")
        ));
        environments.addAll(List.of(
                new EnvironmentView("dev", "Shanghai Dev", "api.dev.local", "可用"),
                new EnvironmentView("staging", "Shanghai Staging", "api.stg.local", "可用"),
                new EnvironmentView("prod", "Primary Prod", "api.veri-agent.local", "只读")
        ));
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
    }

    @Override
    public synchronized PageResponse<DepartmentView> departments(PageQuery pageQuery) {
        return page(departments, pageQuery);
    }

    @Override
    public synchronized DepartmentView createDepartment(String name, AuthUserPrincipal actor) {
        DepartmentView view = new DepartmentView(name, "总部", actor.displayName(), 0, "同步正常");
        departments.add(0, view);
        audit(actor, "创建部门", name);
        return view;
    }

    @Override
    public synchronized DepartmentView department(String key) {
        return requireDepartment(key);
    }

    @Override
    public synchronized DepartmentView updateDepartment(String key, UpdateDepartmentRequest request, AuthUserPrincipal actor) {
        DepartmentView current = requireDepartment(key);
        if ("已停用".equals(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前部门状态不允许编辑");
        }
        DepartmentView updated = replaceDepartment(
                current.name(),
                new DepartmentView(
                        trimOrDefault(request.name(), current.name()),
                        current.parent(),
                        current.lead(),
                        current.members(),
                        current.status()
                )
        );
        audit(actor, "更新部门", updated.name());
        return updated;
    }

    @Override
    public synchronized DepartmentView changeDepartmentStatus(String key, String status, AuthUserPrincipal actor) {
        DepartmentView current = requireDepartment(key);
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "部门状态不支持");
        }
        DepartmentView updated = replaceDepartment(
                current.name(),
                new DepartmentView(
                        current.name(),
                        current.parent(),
                        current.lead(),
                        current.members(),
                        "ENABLED".equals(status) ? "同步正常" : "已停用"
                )
        );
        audit(actor, "ENABLED".equals(status) ? "启用部门" : "停用部门", updated.name());
        return updated;
    }

    @Override
    public synchronized PageResponse<UserView> users(PageQuery pageQuery) {
        return page(users, pageQuery);
    }

    @Override
    public synchronized UserView user(String username) {
        return requireUser(username);
    }

    @Override
    public synchronized UserView createUser(String username, AuthUserPrincipal actor) {
        UserView view = new UserView(username, username, "", "Tester", "质量工程中心", "待激活", "尚未登录");
        users.add(0, view);
        audit(actor, "邀请用户", username);
        return view;
    }

    @Override
    public synchronized UserView updateUser(String username, UpdateUserRequest request, AuthUserPrincipal actor) {
        UserView current = requireUser(username);
        UserView updated = new UserView(
                current.username(),
                trimOrDefault(request.displayName(), current.displayName()),
                trimOrDefault(request.email(), current.email()),
                current.role(),
                current.department(),
                current.status(),
                current.lastSeen()
        );
        replaceUser(updated);
        audit(actor, "更新用户", username);
        return updated;
    }

    @Override
    public synchronized UserView enableUser(String username, AuthUserPrincipal actor) {
        UserView view = replaceUserStatus(username, "启用");
        audit(actor, "启用用户", username);
        return view;
    }

    @Override
    public synchronized UserView disableUser(String username, AuthUserPrincipal actor) {
        if (actor.username().equals(username)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "不能停用当前登录账号");
        }
        UserView view = replaceUserStatus(username, "已停用");
        audit(actor, "停用用户", username);
        return view;
    }

    @Override
    public synchronized UserView lockUser(String username, AuthUserPrincipal actor) {
        if (actor.username().equals(username)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "不能锁定当前登录账号");
        }
        UserView view = replaceUserStatus(username, "已锁定");
        audit(actor, "锁定用户", username);
        return view;
    }

    @Override
    public synchronized UserView unlockUser(String username, AuthUserPrincipal actor) {
        UserView view = replaceUserStatus(username, "启用");
        audit(actor, "解锁用户", username);
        return view;
    }

    @Override
    public synchronized UserView resetUserPassword(String username, String newPassword, AuthUserPrincipal actor) {
        UserView view = replaceUserStatus(username, "启用");
        audit(actor, "重置密码", username);
        return view;
    }

    @Override
    public synchronized PageResponse<RoleView> roles(PageQuery pageQuery) {
        return page(roles, pageQuery);
    }

    @Override
    public synchronized UserView assignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        requireRole(roleCode);
        UserView current = requireUser(username);
        if (hasRole(current.role(), roleCode)) {
            return current;
        }
        UserView updated = replaceUserRole(username, current.role() + " / " + roleCode);
        audit(actor, "分配角色", username + ":" + roleCode);
        return updated;
    }

    @Override
    public synchronized UserView unassignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        UserView current = requireUser(username);
        List<String> roleCodes = List.of(current.role().split(" / ")).stream()
                .filter(role -> !role.equals(roleCode))
                .toList();
        UserView updated = replaceUserRole(username, roleCodes.isEmpty() ? "未分配" : String.join(" / ", roleCodes));
        audit(actor, "解绑角色", username + ":" + roleCode);
        return updated;
    }

    @Override
    public synchronized PageResponse<ProjectView> projects(PageQuery pageQuery, AuthUserPrincipal actor) {
        return page(projects, pageQuery);
    }

    @Override
    public synchronized ProjectView project(String key) {
        return requireProject(key);
    }

    @Override
    public synchronized ProjectView createProject(CreateProjectRequest request, AuthUserPrincipal actor) {
        String name = request.name().trim();
        ProjectView view = new ProjectView(name, "质量工程中心", actor.displayName(), 0, "规划中");
        projects.add(0, view);
        audit(actor, "创建项目", name);
        return view;
    }

    @Override
    public synchronized ProjectView updateProject(String key, UpdateProjectRequest request, AuthUserPrincipal actor) {
        ProjectView current = requireProject(key);
        if ("已归档".equals(current.status()) || "已停用".equals(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前项目状态不允许编辑");
        }
        ProjectView updated = replaceProject(
                current.name(),
                new ProjectView(
                        trimOrDefault(request.name(), current.name()),
                        current.department(),
                        current.owner(),
                        current.apps(),
                        current.status()
                )
        );
        audit(actor, "更新项目", updated.name());
        return updated;
    }

    @Override
    public synchronized ProjectView changeProjectStatus(String key, String status, AuthUserPrincipal actor) {
        ProjectView current = requireProject(key);
        String nextStatusCode = normalizeProjectStatus(status);
        ensureProjectStatusTransition(actor, current.name(), projectStatusCode(current.status()), nextStatusCode);
        String nextStatus = switch (nextStatusCode) {
            case "PREPARING" -> "规划中";
            case "ACTIVE" -> "进行中";
            case "ARCHIVED" -> "已归档";
            case "DISABLED" -> "已停用";
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目状态不支持");
        };
        ProjectView updated = replaceProject(
                current.name(),
                new ProjectView(current.name(), current.department(), current.owner(), current.apps(), nextStatus)
        );
        audit(actor, projectStatusAction(nextStatusCode), updated.name());
        return updated;
    }

    @Override
    public synchronized PageResponse<ProjectMemberView> projectMembers(String projectKey, PageQuery pageQuery) {
        requireProject(projectKey);
        return page(projectMembers, pageQuery);
    }

    @Override
    public synchronized ProjectMemberView addProjectMember(String projectKey, ProjectMemberRequest request, AuthUserPrincipal actor) {
        requireProject(projectKey);
        UserView user = requireUser(request.username().trim());
        projectMembers.removeIf(member -> member.username().equals(user.username()));
        ProjectMemberView view = new ProjectMemberView(
                user.username(),
                user.username(),
                request.roleCode().trim(),
                memberTypeForRole(request.roleCode().trim()),
                "启用"
        );
        projectMembers.add(0, view);
        audit(actor, "添加项目成员", projectKey + ":" + user.username());
        return view;
    }

    @Override
    public synchronized ProjectMemberView removeProjectMember(String projectKey, String username, AuthUserPrincipal actor) {
        requireProject(projectKey);
        ProjectMemberView current = projectMembers.stream()
                .filter(member -> member.username().equals(username))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "项目成员不存在"));
        projectMembers.removeIf(member -> member.username().equals(username));
        ProjectMemberView removed = new ProjectMemberView(
                current.username(),
                current.displayName(),
                current.role(),
                current.memberType(),
                "已移除"
        );
        audit(actor, "移除项目成员", projectKey + ":" + username);
        return removed;
    }

    @Override
    public synchronized PageResponse<ApplicationView> applications(PageQuery pageQuery, AuthUserPrincipal actor) {
        return page(applications, pageQuery);
    }

    @Override
    public synchronized ApplicationView application(String key) {
        return requireApplication(key);
    }

    @Override
    public synchronized ApplicationView createApplication(CreateApplicationRequest request, AuthUserPrincipal actor) {
        String name = request.name().trim();
        String appType = request.appType() == null || request.appType().isBlank() ? "Web" : request.appType().trim();
        ApplicationView view = new ApplicationView(name, appType, actor.displayName(), "v0.1.0", "接入中");
        applications.add(0, view);
        audit(actor, "登记应用", name);
        return view;
    }

    @Override
    public synchronized ApplicationView updateApplication(String key, UpdateApplicationRequest request, AuthUserPrincipal actor) {
        ApplicationView current = requireApplication(key);
        if ("已停用".equals(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前应用状态不允许编辑");
        }
        ApplicationView updated = replaceApplication(
                current.name(),
                new ApplicationView(
                        trimOrDefault(request.name(), current.name()),
                        trimOrDefault(request.appType(), current.type()),
                        current.owner(),
                        current.version(),
                        current.status()
                )
        );
        audit(actor, "更新应用", updated.name());
        return updated;
    }

    @Override
    public synchronized ApplicationView changeApplicationStatus(String key, String status, AuthUserPrincipal actor) {
        ApplicationView current = requireApplication(key);
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应用状态不支持");
        }
        ApplicationView updated = replaceApplication(
                current.name(),
                new ApplicationView(current.name(), current.type(), current.owner(), current.version(), "ENABLED".equals(status) ? "已接入" : "已停用")
        );
        audit(actor, "ENABLED".equals(status) ? "启用应用" : "停用应用", updated.name());
        return updated;
    }

    @Override
    public synchronized PageResponse<ScopedUserRoleView> applicationOwners(String applicationKey, PageQuery pageQuery) {
        requireApplication(applicationKey);
        return page(applicationOwners, pageQuery);
    }

    @Override
    public synchronized ScopedUserRoleView addApplicationOwner(String applicationKey, ScopedUserRoleRequest request, AuthUserPrincipal actor) {
        requireApplication(applicationKey);
        if (!"AppOwner".equals(request.roleCode().trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应用负责人只能绑定 AppOwner 角色");
        }
        UserView user = requireUser(request.username().trim());
        applicationOwners.removeIf(owner -> owner.username().equals(user.username()));
        ScopedUserRoleView view = new ScopedUserRoleView(user.username(), user.username(), "AppOwner", "APPLICATION", "启用");
        applicationOwners.add(0, view);
        audit(actor, "添加应用负责人", applicationKey + ":" + user.username());
        return view;
    }

    @Override
    public synchronized ScopedUserRoleView removeApplicationOwner(String applicationKey, String username, AuthUserPrincipal actor) {
        requireApplication(applicationKey);
        ScopedUserRoleView current = applicationOwners.stream()
                .filter(owner -> owner.username().equals(username))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用负责人不存在"));
        applicationOwners.removeIf(owner -> owner.username().equals(username));
        ScopedUserRoleView removed = new ScopedUserRoleView(
                current.username(),
                current.displayName(),
                current.role(),
                current.scopeType(),
                "已移除"
        );
        audit(actor, "移除应用负责人", applicationKey + ":" + username);
        return removed;
    }

    @Override
    public synchronized PageResponse<EnvironmentView> environments(PageQuery pageQuery, AuthUserPrincipal actor) {
        return page(environments, pageQuery);
    }

    @Override
    public synchronized EnvironmentView environment(String key) {
        return requireEnvironment(key);
    }

    @Override
    public synchronized EnvironmentView createEnvironment(CreateEnvironmentRequest request, AuthUserPrincipal actor) {
        String name = request.name().trim();
        String endpoint = request.apiBaseUrl() == null || request.apiBaseUrl().isBlank()
                ? name + ".local"
                : request.apiBaseUrl().trim();
        EnvironmentView view = new EnvironmentView(name, "Default Cluster", endpoint, "可用");
        environments.add(0, view);
        audit(actor, "新增环境", name);
        return view;
    }

    @Override
    public synchronized EnvironmentView updateEnvironment(String key, UpdateEnvironmentRequest request, AuthUserPrincipal actor) {
        EnvironmentView current = requireEnvironment(key);
        if ("已停用".equals(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前环境状态不允许编辑");
        }
        EnvironmentView updated = replaceEnvironment(
                current.name(),
                new EnvironmentView(
                        trimOrDefault(request.name(), current.name()),
                        current.cluster(),
                        trimOrDefault(request.apiBaseUrl(), current.endpoint()),
                        current.status()
                )
        );
        audit(actor, "更新环境", updated.name());
        return updated;
    }

    @Override
    public synchronized EnvironmentView changeEnvironmentStatus(String key, String status, AuthUserPrincipal actor) {
        EnvironmentView current = requireEnvironment(key);
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "环境状态不支持");
        }
        EnvironmentView updated = replaceEnvironment(
                current.name(),
                new EnvironmentView(current.name(), current.cluster(), current.endpoint(), "ENABLED".equals(status) ? "可用" : "已停用")
        );
        audit(actor, "ENABLED".equals(status) ? "启用环境" : "停用环境", updated.name());
        return updated;
    }

    @Override
    public synchronized PageResponse<ScopedUserRoleView> environmentUsers(String environmentKey, PageQuery pageQuery) {
        requireEnvironment(environmentKey);
        return page(environmentUsers, pageQuery);
    }

    @Override
    public synchronized ScopedUserRoleView addEnvironmentUser(String environmentKey, ScopedUserRoleRequest request, AuthUserPrincipal actor) {
        requireEnvironment(environmentKey);
        String roleCode = request.roleCode().trim();
        if ("AppOwner".equals(roleCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "环境授权用户不能绑定 AppOwner 角色");
        }
        UserView user = requireUser(request.username().trim());
        environmentUsers.removeIf(envUser -> envUser.username().equals(user.username()));
        ScopedUserRoleView view = new ScopedUserRoleView(user.username(), user.username(), roleCode, "ENVIRONMENT", "启用");
        environmentUsers.add(0, view);
        audit(actor, "添加环境授权", environmentKey + ":" + user.username());
        return view;
    }

    @Override
    public synchronized ScopedUserRoleView removeEnvironmentUser(String environmentKey, String username, AuthUserPrincipal actor) {
        requireEnvironment(environmentKey);
        ScopedUserRoleView current = environmentUsers.stream()
                .filter(envUser -> envUser.username().equals(username))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "环境授权用户不存在"));
        environmentUsers.removeIf(envUser -> envUser.username().equals(username));
        ScopedUserRoleView removed = new ScopedUserRoleView(
                current.username(),
                current.displayName(),
                current.role(),
                current.scopeType(),
                "已移除"
        );
        audit(actor, "移除环境授权", environmentKey + ":" + username);
        return removed;
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

    private void audit(AuthUserPrincipal actor, String action, String target) {
        auditLogWriter.record(AuditLogWriter.success(
                actor, action, "management", target, target
        ));
    }

    private void auditDenied(AuthUserPrincipal actor, String action, String target, String reason) {
        auditLogWriter.record(AuditLogWriter.denied(
                actor, action, "management", target, reason
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

    private String integrationKey(String code, String name) {
        String seed = defaultText(code, name);
        String normalized = seed.trim().toLowerCase().replaceAll("[^a-z0-9_-]+", "-").replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? "integration-" + Math.abs(seed.hashCode()) : normalized;
    }

    private String defaultText(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? fallback : normalized;
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
            case "新增环境" -> "environment";
            case "分配角色", "解绑角色", "更新角色" -> "rbac_role_binding";
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
        boolean exists = roles.stream().anyMatch(role -> role.code().equals(roleCode));
        if (!exists) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
    }

    private UserView requireUser(String username) {
        return users.stream()
                .filter(user -> user.username().equals(username))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    private DepartmentView requireDepartment(String key) {
        return departments.stream()
                .filter(department -> department.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "部门不存在"));
    }

    private ProjectView requireProject(String key) {
        return projects.stream()
                .filter(project -> project.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "项目不存在"));
    }

    private ApplicationView requireApplication(String key) {
        return applications.stream()
                .filter(application -> application.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用不存在"));
    }

    private EnvironmentView requireEnvironment(String key) {
        return environments.stream()
                .filter(environment -> environment.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "环境不存在"));
    }

    private ProjectView replaceProject(String key, ProjectView updated) {
        for (int index = 0; index < projects.size(); index++) {
            ProjectView current = projects.get(index);
            if (current.name().equals(key)) {
                projects.set(index, updated);
                return updated;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在");
    }

    private ApplicationView replaceApplication(String key, ApplicationView updated) {
        for (int index = 0; index < applications.size(); index++) {
            ApplicationView current = applications.get(index);
            if (current.name().equals(key)) {
                applications.set(index, updated);
                return updated;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "应用不存在");
    }

    private EnvironmentView replaceEnvironment(String key, EnvironmentView updated) {
        for (int index = 0; index < environments.size(); index++) {
            EnvironmentView current = environments.get(index);
            if (current.name().equals(key)) {
                environments.set(index, updated);
                return updated;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "环境不存在");
    }

    private DepartmentView replaceDepartment(String key, DepartmentView updated) {
        for (int index = 0; index < departments.size(); index++) {
            DepartmentView current = departments.get(index);
            if (current.name().equals(key)) {
                departments.set(index, updated);
                return updated;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "部门不存在");
    }

    private String trimOrDefault(String value, String defaultValue) {
        if (value == null || value.trim().isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private String projectStatusAction(String status) {
        return switch (status) {
            case "ARCHIVED" -> "归档项目";
            case "DISABLED" -> "停用项目";
            case "ACTIVE", "PREPARING" -> "恢复项目";
            default -> "更新项目状态";
        };
    }

    private String normalizeProjectStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("PREPARING", "ACTIVE", "ARCHIVED", "DISABLED").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目状态不支持");
        }
        return normalized;
    }

    private String projectStatusCode(String status) {
        return switch (status) {
            case "规划中" -> "PREPARING";
            case "进行中" -> "ACTIVE";
            case "已归档" -> "ARCHIVED";
            case "已停用" -> "DISABLED";
            default -> "";
        };
    }

    private void ensureProjectStatusTransition(AuthUserPrincipal actor, String projectName, String currentStatus, String nextStatus) {
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
            auditDenied(actor, "项目状态拒绝", projectName, "项目状态流不允许: " + currentStatus + "->" + nextStatus);
            throw new BusinessException(ErrorCode.INVALID_STATE, "项目状态不允许从 " + currentStatus + " 流转到 " + nextStatus);
        }
    }

    private String memberTypeForRole(String roleCode) {
        return "ProjectOwner".equals(roleCode) ? "OWNER" : "MEMBER";
    }

    private boolean hasRole(String roleNames, String roleCode) {
        return List.of(roleNames.split(" / ")).contains(roleCode);
    }

    private UserView replaceUserRole(String username, String role) {
        for (int index = 0; index < users.size(); index++) {
            UserView current = users.get(index);
            if (current.username().equals(username)) {
                UserView updated = new UserView(
                        current.username(),
                        current.displayName(),
                        current.email(),
                        role,
                        current.department(),
                        current.status(),
                        current.lastSeen()
                );
                users.set(index, updated);
                return updated;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
    }

    private UserView replaceUserStatus(String username, String status) {
        for (int index = 0; index < users.size(); index++) {
            UserView current = users.get(index);
            if (current.username().equals(username)) {
                UserView updated = new UserView(
                        current.username(),
                        current.displayName(),
                        current.email(),
                        current.role(),
                        current.department(),
                        status,
                        current.lastSeen()
                );
                users.set(index, updated);
                return updated;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
    }

    private void replaceUser(UserView updated) {
        for (int index = 0; index < users.size(); index++) {
            UserView current = users.get(index);
            if (current.username().equals(updated.username())) {
                users.set(index, updated);
                return;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
    }
}
