package com.songhg.veri.agent.management.application.service;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.management.application.security.ManagementAuthorizationGuard;
import com.songhg.veri.agent.management.application.command.CreateApplicationCommand;
import com.songhg.veri.agent.management.application.command.ScopedUserRoleCommand;
import com.songhg.veri.agent.management.application.command.UpdateApplicationCommand;
import com.songhg.veri.agent.management.application.port.ApplicationOperations;
import com.songhg.veri.agent.management.application.view.ApplicationView;
import com.songhg.veri.agent.management.application.view.ScopedUserRoleView;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.management.application.port.ManagementStoreParams;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.ApplicationRef;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.ProjectRef;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ManagementApplicationService implements ApplicationOperations {

    private final ManagementStore store;
    private final AuditLogWriter auditLogWriter;
    private final ManagementProjectService projectService;
    private final ManagementAuthorizationGuard authorizationGuard;

    ManagementApplicationService(
            ManagementStore store,
            AuditLogWriter auditLogWriter,
            ManagementProjectService projectService,
            ManagementAuthorizationGuard authorizationGuard
    ) {
        this.store = store;
        this.auditLogWriter = auditLogWriter;
        this.projectService = projectService;
        this.authorizationGuard = authorizationGuard;
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationView> applications(PageQuery pageQuery, AuthUserPrincipal actor) {
        return page(store::listApplications, store::countApplications, pageQuery, scope(actor));
    }

    @Transactional(readOnly = true)
    public ApplicationView application(String key) {
        return applicationByKey(key);
    }

    @Transactional
    public ApplicationView createApplication(CreateApplicationCommand request, AuthUserPrincipal actor) {
        String name = request.name().trim();
        String appType = normalizedOrDefault(request.appType(), "WEB_ADMIN");
        String code = normalizedOrGeneratedCode(request.code(), "app");
        String sensitivityLevel = normalizedOrDefault(request.sensitivityLevel(), "INTERNAL");
        boolean allowPublicModel = Boolean.TRUE.equals(request.allowPublicModel());
        ProjectRef project = projectService.resolveProject(request.project(), actor);
        ensureProjectEditable(project.status());
        UUID appId = UUID.randomUUID();
        try {
            update(store::insertApplication, actor, values(
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

    @Transactional
    public ApplicationView updateApplication(String key, UpdateApplicationCommand request, AuthUserPrincipal actor) {
        ApplicationRef application = resolveApplicationStrict(key);
        ensureEnabled(application.status(), "当前应用状态不允许编辑");
        ApplicationView before = applicationByKey(application.id().toString());
        try {
            update(store::updateApplication, actor, values(
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

    @Transactional
    public ApplicationView changeApplicationStatus(String key, String status, AuthUserPrincipal actor) {
        String nextStatus = normalizeEnabledStatus(status, "应用状态不支持");
        authorizationGuard.requireApplicationStatus(actor, nextStatus);
        ApplicationRef application = resolveApplicationStrict(key);
        update(store::changeApplicationStatus, actor, values("applicationId", application.id(), "status", nextStatus));
        ApplicationView updated = applicationByKey(application.id().toString());
        audit(actor, "ENABLED".equals(nextStatus) ? "启用应用" : "停用应用", "application", application.id().toString(), updated.name());
        return updated;
    }

    @Transactional(readOnly = true)
    public PageResponse<ScopedUserRoleView> applicationOwners(String applicationKey, PageQuery pageQuery) {
        ApplicationRef application = resolveApplicationStrict(applicationKey);
        return scopedUserRoles(application.id(), "APPLICATION", "AppOwner", pageQuery);
    }

    @Transactional
    public ScopedUserRoleView addApplicationOwner(String applicationKey, ScopedUserRoleCommand request, AuthUserPrincipal actor) {
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

    private ApplicationRef resolveApplicationStrict(String key) {
        return requireOne(store::findApplicationRef, values("keyword", key), "应用不存在");
    }

    private ApplicationView applicationByKey(String key) {
        return requireOne(store::findApplicationView, values("keyword", key), "应用不存在");
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

    private UUID requireUserId(String username) {
        return requireOne(store::findUserId, values("username", username), "用户不存在");
    }

    private UUID requireRoleId(String roleCode) {
        return requireOne(store::findRoleId, values("roleCode", roleCode), "角色不存在");
    }

    private void bumpUserAuthVersion(UUID userId, AuthUserPrincipal actor) {
        update(store::bumpUserAuthVersion, actor, values("userId", userId));
    }

    private int update(ToIntFunction<ManagementStoreParams> statement, AuthUserPrincipal actor, ManagementStoreParams params) {
        return statement.applyAsInt(withActor(actor, params));
    }

    private <T> PageResponse<T> page(
            Function<ManagementStoreParams, List<T>> listStatement,
            ToLongFunction<ManagementStoreParams> countStatement,
            PageQuery pageQuery,
            ManagementStoreParams extraParams
    ) {
        ManagementStoreParams params = pageParams(pageQuery, extraParams);
        List<T> items = listStatement.apply(params);
        long total = countStatement.applyAsLong(params);
        return PageResponse.of(items, pageQuery.index(), pageQuery.size(), total);
    }

    private ManagementStoreParams pageParams(PageQuery pageQuery, ManagementStoreParams extraParams) {
        ManagementStoreParams params = ManagementStoreParams.copyOf(extraParams);
        params.put("search", pageQuery.search());
        params.put("searchPattern", pageQuery.searchPattern());
        params.put("limit", pageQuery.size());
        params.put("offset", pageQuery.offset());
        return params;
    }

    private ManagementStoreParams scope(AuthUserPrincipal actor) {
        return values("actorId", actor.userId(), "platformScope", hasPlatformScope(actor));
    }

    private ManagementStoreParams withActor(AuthUserPrincipal actor, ManagementStoreParams source) {
        ManagementStoreParams params = ManagementStoreParams.copyOf(source);
        params.put("actorId", actor.userId());
        return params;
    }

    private ManagementStoreParams values(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("参数必须成对出现");
        }
        ManagementStoreParams params = ManagementStoreParams.empty();
        for (int index = 0; index < pairs.length; index += 2) {
            params.put((String) pairs[index], pairs[index + 1]);
        }
        return params;
    }

    private <T> T requireOne(Function<ManagementStoreParams, T> statement, ManagementStoreParams params, String notFoundMessage) {
        ManagementStoreParams normalized = ManagementStoreParams.copyOf(params);
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
