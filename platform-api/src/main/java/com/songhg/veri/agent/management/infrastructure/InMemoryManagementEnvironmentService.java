package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.management.application.port.EnvironmentOperations;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.management.application.command.CreateEnvironmentRequest;
import com.songhg.veri.agent.management.application.command.ScopedUserRoleRequest;
import com.songhg.veri.agent.management.application.command.UpdateEnvironmentRequest;
import com.songhg.veri.agent.management.application.view.EnvironmentConnectivityCheckView;
import com.songhg.veri.agent.management.application.view.EnvironmentView;
import com.songhg.veri.agent.management.application.view.ScopedUserRoleView;
import com.songhg.veri.agent.management.application.view.UserView;
import com.songhg.veri.agent.management.application.port.EnvironmentConnectivityChecker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.Map;

@Profile("local")
@Service
final class InMemoryManagementEnvironmentService implements EnvironmentOperations {

    private final List<EnvironmentView> environments = new ArrayList<>();
    private final List<ScopedUserRoleView> environmentUsers = new ArrayList<>();
    private final Map<String, EnvironmentConnectivityCheckView> environmentConnectivityChecks = new HashMap<>();
    private final InMemoryManagementUserService userService;
    private final AuditLogWriter auditLogWriter;
    private final EnvironmentConnectivityChecker connectivityChecker;

    InMemoryManagementEnvironmentService(
            InMemoryManagementUserService userService,
            AuditLogWriter auditLogWriter,
            EnvironmentConnectivityChecker connectivityChecker
    ) {
        this.userService = userService;
        this.auditLogWriter = auditLogWriter;
        this.connectivityChecker = connectivityChecker;
        environments.addAll(List.of(
                new EnvironmentView("dev", "Shanghai Dev", "api.dev.local", "可用"),
                new EnvironmentView("staging", "Shanghai Staging", "api.stg.local", "可用"),
                new EnvironmentView("prod", "Primary Prod", "api.veri-agent.local", "只读")
        ));
    }

    public synchronized PageResponse<EnvironmentView> environments(PageQuery pageQuery, AuthUserPrincipal actor) {
        return page(environments, pageQuery);
    }

    public synchronized EnvironmentView environment(String key) {
        return requireEnvironment(key);
    }

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

    public synchronized EnvironmentView changeEnvironmentStatus(String key, String status, AuthUserPrincipal actor) {
        EnvironmentView current = requireEnvironment(key);
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "环境状态不支持");
        }
        EnvironmentView updated = replaceEnvironment(
                current.name(),
                new EnvironmentView(
                        current.name(),
                        current.cluster(),
                        current.endpoint(),
                        "ENABLED".equals(status) ? "可用" : "已停用"
                )
        );
        audit(actor, "ENABLED".equals(status) ? "启用环境" : "停用环境", updated.name());
        return updated;
    }

    public synchronized EnvironmentConnectivityCheckView environmentConnectivityCheck(String key) {
        EnvironmentView current = requireEnvironment(key);
        return environmentConnectivityChecks.getOrDefault(
                current.name(),
                EnvironmentConnectivityCheckView.notChecked(current.name())
        );
    }

    public synchronized EnvironmentConnectivityCheckView checkEnvironmentConnectivity(String key, AuthUserPrincipal actor) {
        EnvironmentView current = requireEnvironment(key);
        if ("已停用".equals(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "停用环境不可执行连通性检查");
        }
        EnvironmentConnectivityCheckView result = connectivityChecker.check(current.name(), "", current.endpoint());
        environmentConnectivityChecks.put(current.name(), result);
        audit(actor, "环境连通性检查", current.name());
        return result;
    }

    public synchronized PageResponse<ScopedUserRoleView> environmentUsers(String environmentKey, PageQuery pageQuery) {
        requireEnvironment(environmentKey);
        return page(environmentUsers, pageQuery);
    }

    public synchronized ScopedUserRoleView addEnvironmentUser(
            String environmentKey,
            ScopedUserRoleRequest request,
            AuthUserPrincipal actor
    ) {
        requireEnvironment(environmentKey);
        String roleCode = request.roleCode().trim();
        if ("AppOwner".equals(roleCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "环境授权用户不能绑定 AppOwner 角色");
        }
        UserView user = userService.requireUser(request.username().trim());
        environmentUsers.removeIf(envUser -> envUser.username().equals(user.username()));
        ScopedUserRoleView view = new ScopedUserRoleView(user.username(), user.username(), roleCode, "ENVIRONMENT", "启用");
        environmentUsers.add(0, view);
        audit(actor, "添加环境授权", environmentKey + ":" + user.username());
        return view;
    }

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

    private EnvironmentView requireEnvironment(String key) {
        return environments.stream()
                .filter(environment -> environment.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "环境不存在"));
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
