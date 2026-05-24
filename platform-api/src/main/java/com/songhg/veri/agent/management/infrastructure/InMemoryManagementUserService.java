package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.management.application.UpdateUserRequest;
import com.songhg.veri.agent.management.application.UserView;
import java.util.ArrayList;
import java.util.List;

final class InMemoryManagementUserService {

    private final List<UserView> users = new ArrayList<>();
    private final AuditLogWriter auditLogWriter;

    InMemoryManagementUserService(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
        users.addAll(List.of(
                new UserView("shao.min", "邵敏", "shao.min@example.com", "PlatformAdmin", "质量工程中心", "启用", "今天 10:24"),
                new UserView("he.xu", "何序", "he.xu@example.com", "ProjectOwner", "自动化平台组", "启用", "今天 09:43"),
                new UserView("zhao.wen", "赵文", "zhao.wen@example.com", "Auditor", "业务验收组", "待激活", "尚未登录")
        ));
    }

    PageResponse<UserView> users(PageQuery pageQuery) {
        return page(users, pageQuery);
    }

    UserView user(String username) {
        return requireUser(username);
    }

    UserView createUser(String username, AuthUserPrincipal actor) {
        UserView view = new UserView(username, username, "", "Tester", "质量工程中心", "待激活", "尚未登录");
        users.add(0, view);
        audit(actor, "邀请用户", username);
        return view;
    }

    UserView updateUser(String username, UpdateUserRequest request, AuthUserPrincipal actor) {
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

    UserView enableUser(String username, AuthUserPrincipal actor) {
        UserView view = replaceUserStatus(username, "启用");
        audit(actor, "启用用户", username);
        return view;
    }

    UserView disableUser(String username, AuthUserPrincipal actor) {
        if (actor.username().equals(username)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "不能停用当前登录账号");
        }
        UserView view = replaceUserStatus(username, "已停用");
        audit(actor, "停用用户", username);
        return view;
    }

    UserView lockUser(String username, AuthUserPrincipal actor) {
        if (actor.username().equals(username)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "不能锁定当前登录账号");
        }
        UserView view = replaceUserStatus(username, "已锁定");
        audit(actor, "锁定用户", username);
        return view;
    }

    UserView unlockUser(String username, AuthUserPrincipal actor) {
        UserView view = replaceUserStatus(username, "启用");
        audit(actor, "解锁用户", username);
        return view;
    }

    UserView resetUserPassword(String username, String newPassword, AuthUserPrincipal actor) {
        UserView view = replaceUserStatus(username, "启用");
        audit(actor, "重置密码", username);
        return view;
    }

    UserView assignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        UserView current = requireUser(username);
        if (hasRole(current.role(), roleCode)) {
            return current;
        }
        UserView updated = replaceUserRole(username, current.role() + " / " + roleCode);
        audit(actor, "分配角色", username + ":" + roleCode);
        return updated;
    }

    UserView unassignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        UserView current = requireUser(username);
        List<String> roleCodes = List.of(current.role().split(" / ")).stream()
                .filter(role -> !role.equals(roleCode))
                .toList();
        UserView updated = replaceUserRole(username, roleCodes.isEmpty() ? "未分配" : String.join(" / ", roleCodes));
        audit(actor, "解绑角色", username + ":" + roleCode);
        return updated;
    }

    UserView requireUser(String username) {
        return users.stream()
                .filter(user -> user.username().equals(username))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
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
