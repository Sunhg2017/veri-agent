package com.songhg.veri.agent.management.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.management.application.port.EnvironmentOperations;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.management.application.security.ManagementAuthorizationGuard;
import com.songhg.veri.agent.management.application.command.CreateEnvironmentCommand;
import com.songhg.veri.agent.management.application.command.ScopedUserRoleCommand;
import com.songhg.veri.agent.management.application.command.UpdateEnvironmentCommand;
import com.songhg.veri.agent.management.application.port.EnvironmentConnectivityChecker;
import com.songhg.veri.agent.management.application.view.EnvironmentConnectivityCheckView;
import com.songhg.veri.agent.management.application.view.EnvironmentView;
import com.songhg.veri.agent.management.application.view.ScopedUserRoleView;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.ApplicationRef;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentConnectivityTargetRow;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.EnvironmentRef;
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
class ManagementEnvironmentService implements EnvironmentOperations {

    private final ManagementStore store;
    private final AuditLogWriter auditLogWriter;
    private final EnvironmentConnectivityChecker connectivityChecker;
    private final ObjectMapper objectMapper;
    private final ManagementProjectService projectService;
    private final ManagementAuthorizationGuard authorizationGuard;

    ManagementEnvironmentService(
            ManagementStore store,
            AuditLogWriter auditLogWriter,
            EnvironmentConnectivityChecker connectivityChecker,
            ObjectMapper objectMapper,
            ManagementProjectService projectService,
            ManagementAuthorizationGuard authorizationGuard
    ) {
        this.store = store;
        this.auditLogWriter = auditLogWriter;
        this.connectivityChecker = connectivityChecker;
        this.objectMapper = objectMapper;
        this.projectService = projectService;
        this.authorizationGuard = authorizationGuard;
    }

    @Transactional(readOnly = true)
    public PageResponse<EnvironmentView> environments(PageQuery pageQuery, AuthUserPrincipal actor) {
        return page(store::listEnvironments, store::countEnvironments, pageQuery, scope(actor));
    }

    @Transactional(readOnly = true)
    public EnvironmentView environment(String key) {
        return environmentByKey(key);
    }

    @Transactional
    public EnvironmentView createEnvironment(CreateEnvironmentCommand request, AuthUserPrincipal actor) {
        String name = request.name().trim();
        ProjectRef project = projectService.resolveProject(request.project(), actor);
        ensureProjectEditable(project.status());
        String scopeType = normalizedOrDefault(request.scopeType(), blankToNull(request.application()) == null ? "PROJECT" : "APPLICATION");
        String envType = normalizedOrDefault(request.envType(), "TEST");
        UUID appId = resolveEnvironmentApplicationId(request, project, scopeType);
        UUID envId = UUID.randomUUID();
        String code = normalizedOrGeneratedCode(request.code(), "env");
        String endpoint = normalizedOrDefault(request.apiBaseUrl(), code + ".local");
        try {
            update(store::insertEnvironment, actor, values(
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

    @Transactional
    public EnvironmentView updateEnvironment(String key, UpdateEnvironmentCommand request, AuthUserPrincipal actor) {
        EnvironmentRef environment = resolveEnvironmentStrict(key);
        ensureEnabled(environment.status(), "当前环境状态不允许编辑");
        EnvironmentView before = environmentByKey(environment.id().toString());
        try {
            update(store::updateEnvironment, actor, values(
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

    @Transactional
    public EnvironmentView changeEnvironmentStatus(String key, String status, AuthUserPrincipal actor) {
        String nextStatus = normalizeEnabledStatus(status, "环境状态不支持");
        authorizationGuard.requireEnvironmentStatus(actor, nextStatus);
        EnvironmentRef environment = resolveEnvironmentStrict(key);
        update(store::changeEnvironmentStatus, actor, values("environmentId", environment.id(), "status", nextStatus));
        EnvironmentView updated = environmentByKey(environment.id().toString());
        audit(actor, "ENABLED".equals(nextStatus) ? "启用环境" : "停用环境", "environment", environment.id().toString(), updated.name());
        return updated;
    }

    @Transactional(readOnly = true)
    public EnvironmentConnectivityCheckView environmentConnectivityCheck(String key) {
        EnvironmentConnectivityTargetRow target = resolveEnvironmentConnectivityTarget(key);
        return environmentConnectivityCheckView(target);
    }

    @Transactional
    public EnvironmentConnectivityCheckView checkEnvironmentConnectivity(String key, AuthUserPrincipal actor) {
        EnvironmentConnectivityTargetRow target = resolveEnvironmentConnectivityTarget(key);
        ensureEnabled(target.status(), "停用环境不可执行连通性检查");
        EnvironmentConnectivityCheckView result = connectivityChecker.check(
                target.name(),
                target.webUrl(),
                target.apiBaseUrl()
        );
        update(store::updateEnvironmentHealthCheck, actor, values(
                "environmentId", target.id(),
                "healthCheckJson", environmentConnectivityCheckJson(result)
        ));
        audit(actor, "环境连通性检查", "environment", target.id().toString(), target.name());
        return result;
    }

    @Transactional(readOnly = true)
    public PageResponse<ScopedUserRoleView> environmentUsers(String environmentKey, PageQuery pageQuery) {
        EnvironmentRef environment = resolveEnvironmentStrict(environmentKey);
        return scopedUserRoles(environment.id(), "ENVIRONMENT", "", pageQuery);
    }

    @Transactional
    public ScopedUserRoleView addEnvironmentUser(String environmentKey, ScopedUserRoleCommand request, AuthUserPrincipal actor) {
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

    private EnvironmentRef resolveEnvironmentStrict(String key) {
        return requireOne(store::findEnvironmentRef, values("keyword", key), "环境不存在");
    }

    private EnvironmentConnectivityTargetRow resolveEnvironmentConnectivityTarget(String key) {
        return requireOne(store::findEnvironmentConnectivityTarget, values("keyword", key), "环境不存在");
    }

    private EnvironmentView environmentByKey(String key) {
        return requireOne(store::findEnvironmentView, values("keyword", key), "环境不存在");
    }

    private PageResponse<ScopedUserRoleView> scopedUserRoles(
            UUID scopeId,
            String scopeType,
            String roleCode,
            PageQuery pageQuery
    ) {
        return page(store::listScopedUserRoles, store::countScopedUserRoles, pageQuery, values(
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
        return requireOne(store::findScopedUserRoleByUsername, values(
                "scopeId", scopeId,
                "scopeType", scopeType,
                "roleCode", normalizeSearch(roleCode),
                "username", username
        ), notFoundMessage);
    }

    private UUID resolveEnvironmentApplicationId(
            CreateEnvironmentCommand request,
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
                store::findApplicationRefInProject,
                values("projectId", project.id(), "application", application),
                "应用不存在"
        );
        ensureEnabled(app.status(), "当前应用状态不允许新增专属环境");
        return app.id();
    }

    private void bindScopedRole(
            UUID userId,
            UUID roleId,
            String roleCode,
            String scopeType,
            UUID scopeId,
            AuthUserPrincipal actor
    ) {
        update(store::bindScopedRole, actor, values(
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
        update(store::disableScopedRoles, actor, values(
                "userId", userId,
                "scopeType", scopeType,
                "scopeId", scopeId,
                "roleCode", normalizeSearch(roleCode)
        ));
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

    private String nameJson(String name) {
        return "{\"name\":\"" + escapeJson(name) + "\"}";
    }

    private boolean hasPlatformScope(AuthUserPrincipal actor) {
        return actor.roles().stream().anyMatch(role -> List.of("SuperAdmin", "PlatformAdmin", "Auditor").contains(role));
    }

    private String normalizeSearch(String search) {
        return search == null ? "" : search.trim();
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

    private UUID requireUserId(String username) {
        return requireOne(store::findUserId, values("username", username), "用户不存在");
    }

    private UUID requireRoleId(String roleCode) {
        return requireOne(store::findRoleId, values("roleCode", roleCode), "角色不存在");
    }

    private void bumpUserAuthVersion(UUID userId, AuthUserPrincipal actor) {
        update(store::bumpUserAuthVersion, actor, values("userId", userId));
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
