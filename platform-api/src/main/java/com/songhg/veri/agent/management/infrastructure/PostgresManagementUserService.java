package com.songhg.veri.agent.management.infrastructure;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.audit.AuditLogWriter;
import com.songhg.veri.agent.management.application.port.UserOperations;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.management.application.command.UpdateUserCommand;
import com.songhg.veri.agent.management.application.view.UserView;
import com.songhg.veri.agent.management.infrastructure.mapper.ManagementMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("db")
@Service
class PostgresManagementUserService implements UserOperations {

    private final ManagementMapper mapper;
    private final AuditLogWriter auditLogWriter;
    private final PasswordEncoder passwordEncoder;

    PostgresManagementUserService(
            ManagementMapper mapper,
            AuditLogWriter auditLogWriter,
            PasswordEncoder passwordEncoder
    ) {
        this.mapper = mapper;
        this.auditLogWriter = auditLogWriter;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserView> users(PageQuery pageQuery) {
        return page(mapper::listUsers, mapper::countUsers, pageQuery, values());
    }

    @Transactional(readOnly = true)
    public UserView user(String username) {
        return userByUsername(username);
    }

    @Transactional
    public UserView createUser(String username, AuthUserPrincipal actor) {
        UUID userId = UUID.randomUUID();
        try {
            update(mapper::insertUser, actor, values("userId", userId, "username", username));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户账号已存在");
        }
        bindRoleIfPresent(userId, "Tester", "PLATFORM", null, actor);
        audit(actor, "邀请用户", "user", userId.toString(), username);
        return new UserView(username, username, "", "Tester", "未分配", "待激活", "尚未登录");
    }

    @Transactional
    public UserView updateUser(String username, UpdateUserCommand request, AuthUserPrincipal actor) {
        UserView before = userByUsername(username);
        try {
            int rows = update(mapper::updateUser, actor, values(
                    "username", username,
                    "displayName", blankToNull(request.displayName()),
                    "email", blankToNull(request.email())
            ));
            ensureUserUpdated(rows);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户邮箱已存在");
        }
        UserView updated = userByUsername(username);
        auditChange(actor, "更新用户", "user", username, updated.username(),
                nameJson(before.displayName()), nameJson(updated.displayName()), null);
        return updated;
    }

    @Transactional
    public UserView enableUser(String username, AuthUserPrincipal actor) {
        ensureUserUpdated(update(mapper::enableUser, actor, values("username", username)));
        audit(actor, "启用用户", "user", username, username);
        return userByUsername(username);
    }

    @Transactional
    public UserView disableUser(String username, AuthUserPrincipal actor) {
        if (actor.username().equals(username)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "不能停用当前登录账号");
        }
        ensureUserUpdated(update(mapper::disableUser, actor, values("username", username)));
        audit(actor, "停用用户", "user", username, username);
        return userByUsername(username);
    }

    @Transactional
    public UserView lockUser(String username, AuthUserPrincipal actor) {
        if (actor.username().equals(username)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "不能锁定当前登录账号");
        }
        ensureUserUpdated(update(mapper::lockUser, actor, values("username", username)));
        audit(actor, "锁定用户", "user", username, username);
        return userByUsername(username);
    }

    @Transactional
    public UserView unlockUser(String username, AuthUserPrincipal actor) {
        ensureUserUpdated(update(mapper::unlockUser, actor, values("username", username)));
        audit(actor, "解锁用户", "user", username, username);
        return userByUsername(username);
    }

    @Transactional
    public UserView resetUserPassword(String username, String newPassword, AuthUserPrincipal actor) {
        ensureUserUpdated(update(mapper::resetUserPassword, actor, values(
                "username", username,
                "passwordHash", passwordEncoder.encode(newPassword)
        )));
        audit(actor, "重置密码", "user", username, username);
        return userByUsername(username);
    }

    private void bindRoleIfPresent(
            UUID userId,
            String roleCode,
            String scopeType,
            UUID scopeId,
            AuthUserPrincipal actor
    ) {
        UUID roleId = mapper.findRoleId(values("roleCode", roleCode));
        if (roleId == null) {
            return;
        }
        update(mapper::bindRoleIfPresent, actor, values(
                "userId", userId,
                "roleId", roleId,
                "roleCode", roleCode,
                "scopeType", scopeType,
                "scopeId", scopeId
        ));
    }

    private UserView userByUsername(String username) {
        return requireOne(mapper::findUserByUsername, values("username", username), "用户不存在");
    }

    private void ensureUserUpdated(int rows) {
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
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

    private String nameJson(String name) {
        return "{\"name\":\"" + escapeJson(name) + "\"}";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
