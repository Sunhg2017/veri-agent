package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.management.application.security.ManagementAuthorizationGuard;
import com.songhg.veri.agent.management.application.command.UpdateDepartmentRequest;
import com.songhg.veri.agent.management.application.port.DepartmentOperations;
import com.songhg.veri.agent.management.application.view.DepartmentView;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("local")
@Service
final class InMemoryManagementDepartmentService implements DepartmentOperations {

    private final List<DepartmentView> departments = new ArrayList<>();
    private final AuditLogWriter auditLogWriter;
    private final ManagementAuthorizationGuard authorizationGuard;

    InMemoryManagementDepartmentService(AuditLogWriter auditLogWriter, ManagementAuthorizationGuard authorizationGuard) {
        this.auditLogWriter = auditLogWriter;
        this.authorizationGuard = authorizationGuard;
        departments.addAll(List.of(
                new DepartmentView("质量工程中心", "总部", "邵敏", 68, "同步正常"),
                new DepartmentView("自动化平台组", "质量工程中心", "何序", 16, "同步正常"),
                new DepartmentView("业务验收组", "质量工程中心", "赵文", 23, "待确认")
        ));
    }

    public synchronized PageResponse<DepartmentView> departments(PageQuery pageQuery) {
        return page(departments, pageQuery);
    }

    public synchronized DepartmentView createDepartment(String name, AuthUserPrincipal actor) {
        DepartmentView view = new DepartmentView(name, "总部", actor.displayName(), 0, "同步正常");
        departments.add(0, view);
        audit(actor, "创建部门", name);
        return view;
    }

    public synchronized DepartmentView department(String key) {
        return requireDepartment(key);
    }

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

    public synchronized DepartmentView changeDepartmentStatus(String key, String status, AuthUserPrincipal actor) {
        DepartmentView current = requireDepartment(key);
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "部门状态不支持");
        }
        authorizationGuard.requireDepartmentStatus(actor, status);
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

    private DepartmentView requireDepartment(String key) {
        return departments.stream()
                .filter(department -> department.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "部门不存在"));
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

    private <T> PageResponse<T> page(List<T> source, PageQuery pageQuery) {
        String keyword = pageQuery.search().toLowerCase();
        List<T> filtered = source.stream()
                .filter(item -> keyword.isBlank() || item.toString().toLowerCase().contains(keyword))
                .toList();
        int from = Math.min(pageQuery.offset(), filtered.size());
        int to = Math.min(from + pageQuery.size(), filtered.size());
        return PageResponse.of(filtered.subList(from, to), pageQuery.index(), pageQuery.size(), filtered.size());
    }

    private void audit(AuthUserPrincipal actor, String action, String target) {
        auditLogWriter.record(AuditLogWriter.success(
                actor, action, "management", target, target
        ));
    }
}
