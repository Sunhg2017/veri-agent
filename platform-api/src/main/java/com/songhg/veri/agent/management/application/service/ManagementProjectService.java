package com.songhg.veri.agent.management.application.service;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.management.application.security.ManagementAuthorizationGuard;
import com.songhg.veri.agent.management.application.command.CreateProjectCommand;
import com.songhg.veri.agent.management.application.command.ProjectMemberCommand;
import com.songhg.veri.agent.management.application.command.UpdateProjectCommand;
import com.songhg.veri.agent.management.application.port.ProjectOperations;
import com.songhg.veri.agent.management.application.view.ProjectMemberView;
import com.songhg.veri.agent.management.application.view.ProjectView;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.ProjectRef;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ManagementProjectService implements ProjectOperations {

    private final ManagementStore store;
    private final AuditLogWriter auditLogWriter;
    private final ManagementDeniedAuditRecorder deniedAuditRecorder;
    private final ManagementAuthorizationGuard authorizationGuard;

    ManagementProjectService(
            ManagementStore store,
            AuditLogWriter auditLogWriter,
            ManagementDeniedAuditRecorder deniedAuditRecorder,
            ManagementAuthorizationGuard authorizationGuard
    ) {
        this.store = store;
        this.auditLogWriter = auditLogWriter;
        this.deniedAuditRecorder = deniedAuditRecorder;
        this.authorizationGuard = authorizationGuard;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectView> projects(PageQuery pageQuery, AuthUserPrincipal actor) {
        return page(store::listProjects, store::countProjects, pageQuery, scope(actor));
    }

    @Transactional(readOnly = true)
    public ProjectView project(String key) {
        return projectByKey(key);
    }

    @Transactional
    public ProjectView createProject(CreateProjectCommand request, AuthUserPrincipal actor) {
        UUID projectId = UUID.randomUUID();
        String name = request.name().trim();
        String code = normalizedOrGeneratedCode(request.code(), "prj");
        String sensitivityLevel = normalizedOrDefault(request.sensitivityLevel(), "INTERNAL");
        boolean allowPublicModel = Boolean.TRUE.equals(request.allowPublicModel());
        try {
            update(store::insertProject, actor, values(
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

    @Transactional
    public ProjectView updateProject(String key, UpdateProjectCommand request, AuthUserPrincipal actor) {
        ProjectRef project = resolveProjectStrict(key);
        ensureProjectEditable(project.status());
        ProjectView before = projectByKey(project.id().toString());
        try {
            update(store::updateProject, actor, values(
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

    @Transactional
    public ProjectView changeProjectStatus(String key, String status, AuthUserPrincipal actor) {
        String nextStatus = normalizeProjectStatus(status);
        authorizationGuard.requireProjectStatus(actor, nextStatus);
        ProjectRef project = resolveProjectStrict(key);
        ensureProjectStatusTransition(actor, project, nextStatus);
        update(store::changeProjectStatus, actor, values("projectId", project.id(), "status", nextStatus));
        ProjectView updated = projectByKey(project.id().toString());
        audit(actor, projectStatusAction(nextStatus), "project", project.id().toString(), updated.name());
        return updated;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectMemberView> projectMembers(String projectKey, PageQuery pageQuery) {
        ProjectRef project = resolveProjectStrict(projectKey);
        return page(store::listProjectMembers, store::countProjectMembers, pageQuery, values("projectId", project.id()));
    }

    @Transactional
    public ProjectMemberView addProjectMember(String projectKey, ProjectMemberCommand request, AuthUserPrincipal actor) {
        ProjectRef project = resolveProjectStrict(projectKey);
        ensureProjectEditable(project.status());
        String username = request.username().trim();
        String roleCode = request.roleCode().trim();
        UUID userId = requireUserId(username);
        UUID roleId = requireRoleId(roleCode);
        String memberType = memberTypeForRole(roleCode);
        update(store::upsertProjectMember, actor, values("projectId", project.id(), "userId", userId, "memberType", memberType));
        bindProjectRole(userId, roleId, roleCode, project.id(), actor);
        bumpUserAuthVersion(userId, actor);
        ProjectMemberView view = projectMemberByUsername(project.id(), username);
        audit(actor, "添加项目成员", "project_member", project.id() + ":" + userId, project.name() + ":" + username);
        return view;
    }

    @Transactional
    public ProjectMemberView removeProjectMember(String projectKey, String username, AuthUserPrincipal actor) {
        ProjectRef project = resolveProjectStrict(projectKey);
        UUID userId = requireUserId(username);
        ProjectMemberView current = projectMemberByUsername(project.id(), username);
        int rows = update(store::deleteProjectMember, actor, values("projectId", project.id(), "userId", userId));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目成员不存在");
        }
        update(store::disableProjectRoleBindings, actor, values("projectId", project.id(), "userId", userId));
        bumpUserAuthVersion(userId, actor);
        audit(actor, "移除项目成员", "project_member", project.id() + ":" + userId, project.name() + ":" + username);
        return new ProjectMemberView(current.username(), current.displayName(), current.role(), current.memberType(), "已移除");
    }

    @Transactional
    public ProjectRef resolveProject(String project, AuthUserPrincipal actor) {
        String keyword = blankToNull(project);
        if (keyword == null) {
            return new ProjectRef(ensureDefaultProject(actor), "默认项目", "ACTIVE");
        }
        return resolveProjectStrict(keyword);
    }

    @Transactional(readOnly = true)
    public ProjectRef resolveProjectStrict(String key) {
        return requireOne(store::findProjectRef, values("keyword", key), "项目不存在");
    }

    private void insertProjectOwner(UUID projectId, AuthUserPrincipal actor) {
        update(store::insertProjectOwner, actor, values("projectId", projectId));
    }

    private UUID ensureDefaultProject(AuthUserPrincipal actor) {
        UUID existing = store.findDefaultProjectId(values());
        if (existing != null) {
            return existing;
        }
        UUID projectId = UUID.randomUUID();
        update(store::insertDefaultProject, actor, values("projectId", projectId));
        insertProjectOwner(projectId, actor);
        UUID created = store.findDefaultProjectId(values());
        return created == null ? projectId : created;
    }

    private ProjectView projectByKey(String key) {
        return requireOne(store::findProjectView, values("keyword", key), "项目不存在");
    }

    private ProjectMemberView projectMemberByUsername(UUID projectId, String username) {
        return requireOne(store::findProjectMemberByUsername, values("projectId", projectId, "username", username), "项目成员不存在");
    }

    private void bindProjectRole(
            UUID userId,
            UUID roleId,
            String roleCode,
            UUID projectId,
            AuthUserPrincipal actor
    ) {
        update(store::bindProjectRole, actor, values(
                "userId", userId,
                "roleId", roleId,
                "roleCode", roleCode,
                "projectId", projectId
        ));
    }

    private UUID requireUserId(String username) {
        return requireOne(store::findUserId, values("username", username), "用户不存在");
    }

    private UUID requireRoleId(String roleCode) {
        return requireOne(store::findRoleId, values("roleCode", roleCode), "角色不存在");
    }

    private void bumpUserAuthVersion(UUID userId, AuthUserPrincipal actor) {
        update(store::bumpUserAuthVersion, actor, values("userId", userId));
    }

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

    private boolean hasPlatformScope(AuthUserPrincipal actor) {
        return actor.roles().stream().anyMatch(role -> List.of("SuperAdmin", "PlatformAdmin", "Auditor").contains(role));
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

    private String normalizeProjectStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("PREPARING", "ACTIVE", "ARCHIVED", "DISABLED").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目状态不支持");
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

    private String nameJson(String name) {
        return "{\"name\":\"" + escapeJson(name) + "\"}";
    }

    private String nextCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
