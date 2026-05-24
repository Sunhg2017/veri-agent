package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.management.api.request.CreateRoleRequest;
import com.songhg.veri.agent.management.api.request.UpdateRoleRequest;
import com.songhg.veri.agent.management.api.response.PermissionView;
import com.songhg.veri.agent.management.api.response.RoleDetailView;
import com.songhg.veri.agent.management.api.response.RoleView;
import com.songhg.veri.agent.management.api.response.UserView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class InMemoryManagementRoleService {

    private final List<RoleView> roles = new ArrayList<>();
    private final List<PermissionView> permissions = new ArrayList<>();
    private final Map<String, List<String>> rolePermissions = new HashMap<>();
    private final InMemoryManagementUserService userService;
    private final AuditLogWriter auditLogWriter;

    InMemoryManagementRoleService(InMemoryManagementUserService userService, AuditLogWriter auditLogWriter) {
        this.userService = userService;
        this.auditLogWriter = auditLogWriter;
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
    }

    PageResponse<RoleView> roles(PageQuery pageQuery) {
        return page(roles, pageQuery);
    }

    PageResponse<PermissionView> permissions(PageQuery pageQuery) {
        return page(permissions, pageQuery);
    }

    RoleDetailView role(String code) {
        RoleView role = requireRoleView(code);
        return roleDetail(role);
    }

    RoleDetailView createRole(CreateRoleRequest request, Set<String> assignablePermissions, AuthUserPrincipal actor) {
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

    RoleDetailView updateRole(String code, UpdateRoleRequest request, Set<String> assignablePermissions, AuthUserPrincipal actor) {
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

    RoleDetailView changeRoleStatus(String code, String status, AuthUserPrincipal actor) {
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

    UserView assignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        requireRole(roleCode);
        return userService.assignUserRole(username, roleCode, actor);
    }

    UserView unassignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        return userService.unassignUserRole(username, roleCode, actor);
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

    private String defaultText(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? fallback : normalized;
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
