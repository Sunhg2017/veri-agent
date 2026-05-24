package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.management.application.CreateApplicationRequest;
import com.songhg.veri.agent.management.application.ScopedUserRoleRequest;
import com.songhg.veri.agent.management.application.UpdateApplicationRequest;
import com.songhg.veri.agent.management.application.ApplicationView;
import com.songhg.veri.agent.management.application.ScopedUserRoleView;
import com.songhg.veri.agent.management.application.UserView;
import java.util.ArrayList;
import java.util.List;

final class InMemoryManagementApplicationService {

    private final List<ApplicationView> applications = new ArrayList<>();
    private final List<ScopedUserRoleView> applicationOwners = new ArrayList<>();
    private final InMemoryManagementUserService userService;
    private final AuditLogWriter auditLogWriter;

    InMemoryManagementApplicationService(
            InMemoryManagementUserService userService,
            AuditLogWriter auditLogWriter
    ) {
        this.userService = userService;
        this.auditLogWriter = auditLogWriter;
        applications.addAll(List.of(
                new ApplicationView("veri-agent-api", "Backend", "平台组", "v0.3.2", "已接入"),
                new ApplicationView("portal-web", "Frontend", "平台组", "v0.1.0", "接入中"),
                new ApplicationView("mobile-client", "Mobile", "端体验组", "v2.8.1", "待接入")
        ));
    }

    PageResponse<ApplicationView> applications(PageQuery pageQuery, AuthUserPrincipal actor) {
        return page(applications, pageQuery);
    }

    ApplicationView application(String key) {
        return requireApplication(key);
    }

    ApplicationView createApplication(CreateApplicationRequest request, AuthUserPrincipal actor) {
        String name = request.name().trim();
        String appType = request.appType() == null || request.appType().isBlank() ? "Web" : request.appType().trim();
        ApplicationView view = new ApplicationView(name, appType, actor.displayName(), "v0.1.0", "接入中");
        applications.add(0, view);
        audit(actor, "登记应用", name);
        return view;
    }

    ApplicationView updateApplication(String key, UpdateApplicationRequest request, AuthUserPrincipal actor) {
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

    ApplicationView changeApplicationStatus(String key, String status, AuthUserPrincipal actor) {
        ApplicationView current = requireApplication(key);
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应用状态不支持");
        }
        ApplicationView updated = replaceApplication(
                current.name(),
                new ApplicationView(
                        current.name(),
                        current.type(),
                        current.owner(),
                        current.version(),
                        "ENABLED".equals(status) ? "已接入" : "已停用"
                )
        );
        audit(actor, "ENABLED".equals(status) ? "启用应用" : "停用应用", updated.name());
        return updated;
    }

    PageResponse<ScopedUserRoleView> applicationOwners(String applicationKey, PageQuery pageQuery) {
        requireApplication(applicationKey);
        return page(applicationOwners, pageQuery);
    }

    ScopedUserRoleView addApplicationOwner(
            String applicationKey,
            ScopedUserRoleRequest request,
            AuthUserPrincipal actor
    ) {
        requireApplication(applicationKey);
        if (!"AppOwner".equals(request.roleCode().trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "应用负责人只能绑定 AppOwner 角色");
        }
        UserView user = userService.requireUser(request.username().trim());
        applicationOwners.removeIf(owner -> owner.username().equals(user.username()));
        ScopedUserRoleView view = new ScopedUserRoleView(user.username(), user.username(), "AppOwner", "APPLICATION", "启用");
        applicationOwners.add(0, view);
        audit(actor, "添加应用负责人", applicationKey + ":" + user.username());
        return view;
    }

    ScopedUserRoleView removeApplicationOwner(String applicationKey, String username, AuthUserPrincipal actor) {
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

    private ApplicationView requireApplication(String key) {
        return applications.stream()
                .filter(application -> application.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用不存在"));
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
