package com.songhg.veri.agent.management.application.service;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.management.application.security.ManagementAuthorizationGuard;
import com.songhg.veri.agent.management.application.command.UpdateDepartmentCommand;
import com.songhg.veri.agent.management.application.port.DepartmentOperations;
import com.songhg.veri.agent.management.application.view.DepartmentView;
import com.songhg.veri.agent.management.application.port.ManagementStore;
import com.songhg.veri.agent.management.application.port.ManagementStoreParams;
import com.songhg.veri.agent.management.application.port.ManagementStoreRows.DepartmentRef;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ManagementDepartmentService implements DepartmentOperations {

    private final ManagementStore store;
    private final AuditLogWriter auditLogWriter;
    private final ManagementAuthorizationGuard authorizationGuard;

    ManagementDepartmentService(
            ManagementStore store,
            AuditLogWriter auditLogWriter,
            ManagementAuthorizationGuard authorizationGuard
    ) {
        this.store = store;
        this.auditLogWriter = auditLogWriter;
        this.authorizationGuard = authorizationGuard;
    }

    @Transactional(readOnly = true)
    public PageResponse<DepartmentView> departments(PageQuery pageQuery) {
        return page(store::listDepartments, store::countDepartments, pageQuery, values());
    }

    @Transactional
    public DepartmentView createDepartment(String name, AuthUserPrincipal actor) {
        UUID deptId = UUID.randomUUID();
        String code = nextCode("dept");
        try {
            update(store::insertDepartment, actor, values(
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

    @Transactional(readOnly = true)
    public DepartmentView department(String key) {
        return departmentByKey(key);
    }

    @Transactional
    public DepartmentView updateDepartment(String key, UpdateDepartmentCommand request, AuthUserPrincipal actor) {
        DepartmentRef department = resolveDepartmentStrict(key);
        ensureEnabled(department.status(), "当前部门状态不允许编辑");
        DepartmentView before = departmentByKey(department.id().toString());
        try {
            update(store::updateDepartment, actor, values(
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

    @Transactional
    public DepartmentView changeDepartmentStatus(String key, String status, AuthUserPrincipal actor) {
        String nextStatus = normalizeEnabledStatus(status, "部门状态不支持");
        authorizationGuard.requireDepartmentStatus(actor, nextStatus);
        DepartmentRef department = resolveDepartmentStrict(key);
        update(store::changeDepartmentStatus, actor, values("deptId", department.id(), "status", nextStatus));
        DepartmentView updated = departmentByKey(department.id().toString());
        audit(actor, "ENABLED".equals(nextStatus) ? "启用部门" : "停用部门", "department", department.id().toString(), updated.name());
        return updated;
    }

    private void insertDepartmentManager(UUID deptId, AuthUserPrincipal actor) {
        update(store::insertDepartmentManager, actor, values("deptId", deptId));
    }

    private DepartmentRef resolveDepartmentStrict(String key) {
        return requireOne(store::findDepartmentRef, values("keyword", key), "部门不存在");
    }

    private DepartmentView departmentByKey(String key) {
        return requireOne(store::findDepartmentView, values("keyword", key), "部门不存在");
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

    private String blankToNull(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
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
