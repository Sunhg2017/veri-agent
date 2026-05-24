package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.management.application.security.ManagementAuthorizationGuard;
import com.songhg.veri.agent.management.application.command.CreateRoleCommand;
import com.songhg.veri.agent.management.application.command.UpdateRoleCommand;
import com.songhg.veri.agent.management.application.port.RoleOperations;
import com.songhg.veri.agent.management.application.view.PermissionView;
import com.songhg.veri.agent.management.application.view.RoleDetailView;
import com.songhg.veri.agent.management.application.view.RoleView;
import com.songhg.veri.agent.management.application.view.UserView;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapper;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapperRows.RoleRow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("db")
@Service
class PostgresManagementRoleService implements RoleOperations {

    private final ManagementMapper mapper;
    private final AuditLogWriter auditLogWriter;
    private final ManagementAuthorizationGuard authorizationGuard;

    PostgresManagementRoleService(
            ManagementMapper mapper,
            AuditLogWriter auditLogWriter,
            ManagementAuthorizationGuard authorizationGuard
    ) {
        this.mapper = mapper;
        this.auditLogWriter = auditLogWriter;
        this.authorizationGuard = authorizationGuard;
    }

    @Transactional(readOnly = true)
    public PageResponse<RoleView> roles(PageQuery pageQuery) {
        return page(mapper::listRoles, mapper::countRoles, pageQuery, values());
    }

    @Transactional(readOnly = true)
    public PageResponse<PermissionView> permissions(PageQuery pageQuery) {
        return page(mapper::listPermissions, mapper::countPermissions, pageQuery, values());
    }

    @Transactional(readOnly = true)
    public RoleDetailView role(String code) {
        return roleDetail(requireRoleRow(code));
    }

    @Transactional
    public RoleDetailView createRole(CreateRoleCommand request, AuthUserPrincipal actor) {
        String code = request.code().trim();
        String name = request.name().trim();
        String scopeType = request.scopeType().trim();
        String description = blankToNull(request.description());
        List<String> permissionCodes = normalizePermissionCodes(request.permissionCodes());
        ensureAssignablePermissions(permissionCodes, authorizationGuard.assignablePermissions(actor));
        ensureEnabledPermissions(permissionCodes);
        UUID roleId = UUID.randomUUID();
        try {
            update(mapper::insertRole, actor, values(
                    "roleId", roleId,
                    "code", code,
                    "name", name,
                    "scopeType", scopeType,
                    "description", description
            ));
            replaceRolePermissions(roleId, permissionCodes, actor);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "角色编码已存在");
        }
        RoleDetailView created = roleDetail(requireRoleRow(code));
        audit(actor, "创建角色", "rbac_role", roleId.toString(), code);
        return created;
    }

    @Transactional
    public RoleDetailView updateRole(String code, UpdateRoleCommand request, AuthUserPrincipal actor) {
        RoleRow role = requireRoleRow(code);
        ensureCustomRole(role);
        RoleDetailView before = roleDetail(role);
        String name = blankToNull(request.name());
        String scopeType = blankToNull(request.scopeType());
        String description = request.description() == null ? null : request.description().trim();
        List<String> permissionCodes = null;
        if (request.permissionCodes() != null) {
            permissionCodes = normalizePermissionCodes(request.permissionCodes());
            ensureAssignablePermissions(permissionCodes, authorizationGuard.assignablePermissions(actor));
            ensureEnabledPermissions(permissionCodes);
        }
        update(mapper::updateRole, actor, values(
                "roleId", role.id(),
                "name", name,
                "scopeType", scopeType,
                "description", description
        ));
        if (permissionCodes != null) {
            replaceRolePermissions(role.id(), permissionCodes, actor);
        }
        bumpUsersAuthVersionByRole(role.id(), actor);
        RoleDetailView updated = roleDetail(requireRoleRow(code));
        auditChange(actor, "更新角色", "rbac_role", role.id().toString(), updated.code(),
                roleJson(before), roleJson(updated), null);
        return updated;
    }

    @Transactional
    public RoleDetailView changeRoleStatus(String code, String status, AuthUserPrincipal actor) {
        RoleRow role = requireRoleRow(code);
        ensureCustomRole(role);
        String nextStatus = normalizeEnabledStatus(status, "角色状态只支持 ENABLED 或 DISABLED");
        update(mapper::changeRoleStatus, actor, values("roleId", role.id(), "status", nextStatus));
        bumpUsersAuthVersionByRole(role.id(), actor);
        RoleDetailView updated = roleDetail(requireRoleRow(code));
        audit(actor, "ENABLED".equals(nextStatus) ? "启用角色" : "停用角色", "rbac_role", role.id().toString(), updated.code());
        return updated;
    }

    @Transactional
    public UserView assignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        UUID userId = requireUserId(username);
        UUID roleId = requireRoleId(roleCode);
        update(mapper::assignUserRole, actor, values("userId", userId, "roleId", roleId, "roleCode", roleCode));
        bumpUserAuthVersion(userId, actor);
        audit(actor, "分配角色", "rbac_role_binding", userId + ":" + roleCode, username + ":" + roleCode);
        return userByUsername(username);
    }

    @Transactional
    public UserView unassignUserRole(String username, String roleCode, AuthUserPrincipal actor) {
        UUID userId = requireUserId(username);
        int rows = update(mapper::unassignUserRole, actor, values("userId", userId, "roleCode", roleCode));
        if (rows > 0) {
            bumpUserAuthVersion(userId, actor);
        }
        audit(actor, "解绑角色", "rbac_role_binding", userId + ":" + roleCode, username + ":" + roleCode);
        return userByUsername(username);
    }

    private RoleRow requireRoleRow(String code) {
        String roleCode = blankToNull(code);
        if (roleCode == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        return requireOne(mapper::findRoleRow, values("roleCode", roleCode), "角色不存在");
    }

    private RoleDetailView roleDetail(RoleRow row) {
        return new RoleDetailView(
                row.code(),
                row.name(),
                row.scopeType(),
                "ENABLED".equals(row.status()) ? "启用" : "已停用",
                row.description(),
                row.system(),
                row.builtin(),
                row.version(),
                mapper.listRolePermissionCodes(values("roleId", row.id()))
        );
    }

    private void ensureCustomRole(RoleRow row) {
        if (row.system() || row.builtin()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "内置角色不可编辑或停用");
        }
    }

    private List<String> normalizePermissionCodes(List<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "角色至少需要一个权限点");
        }
        Set<String> normalizedCodes = new LinkedHashSet<>();
        for (String permissionCode : permissionCodes) {
            String normalized = blankToNull(permissionCode);
            if (normalized == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "权限点编码不能为空");
            }
            normalizedCodes.add(normalized);
        }
        if (normalizedCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "角色至少需要一个权限点");
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

    private void ensureEnabledPermissions(List<String> permissionCodes) {
        Set<String> enabled = new LinkedHashSet<>(mapper.listEnabledPermissionCodes(values("permissionCodes", permissionCodes)));
        List<String> missing = permissionCodes.stream()
                .filter(permissionCode -> !enabled.contains(permissionCode))
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "权限点不存在或已停用: " + String.join(",", missing));
        }
    }

    private void replaceRolePermissions(UUID roleId, List<String> permissionCodes, AuthUserPrincipal actor) {
        update(mapper::softDeleteRolePermissions, actor, values("roleId", roleId));
        update(mapper::insertRolePermissions, actor, values("roleId", roleId, "permissionCodes", permissionCodes));
    }

    private void bumpUsersAuthVersionByRole(UUID roleId, AuthUserPrincipal actor) {
        update(mapper::bumpUsersAuthVersionByRole, actor, values("roleId", roleId));
    }

    private UserView userByUsername(String username) {
        return requireOne(mapper::findUserByUsername, values("username", username), "用户不存在");
    }

    private UUID requireUserId(String username) {
        return requireOne(mapper::findUserId, values("username", username), "用户不存在");
    }

    private UUID requireRoleId(String roleCode) {
        return requireOne(mapper::findRoleId, values("roleCode", roleCode), "角色不存在");
    }

    private void bumpUserAuthVersion(UUID userId, AuthUserPrincipal actor) {
        update(mapper::bumpUserAuthVersion, actor, values("userId", userId));
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
        T value = statement.apply(params);
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

    private String normalizeEnabledStatus(String status, String message) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("ENABLED", "DISABLED").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private String roleJson(RoleDetailView role) {
        return "{\"code\":\"" + escapeJson(role.code()) + "\","
                + "\"name\":\"" + escapeJson(role.name()) + "\","
                + "\"scopeType\":\"" + escapeJson(role.scopeType()) + "\","
                + "\"status\":\"" + escapeJson(role.status()) + "\","
                + "\"permissionCodes\":" + stringArrayJson(role.permissionCodes()) + ","
                + "\"version\":" + role.version() + "}";
    }

    private String stringArrayJson(List<String> values) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append('"').append(escapeJson(values.get(index))).append('"');
        }
        return json.append(']').toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
