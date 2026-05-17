package com.songhg.veri.agent.bootstrap.infrastructure;

import com.songhg.veri.agent.bootstrap.domain.BootstrapStateStore;
import com.songhg.veri.agent.bootstrap.domain.BootstrapUserDraft;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Profile("db")
@Repository
public class PostgresBootstrapStateStore implements BootstrapStateStore {

    private static final String SUPER_ADMIN_ROLE = "SuperAdmin";
    private static final String BOOTSTRAP_LOCK_NAME = "veri-agent:bootstrap:super-admin";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresBootstrapStateStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean hasSuperAdmin() {
        Boolean initialized = jdbcTemplate.queryForObject("""
                select exists (
                    select 1
                    from iam_user u
                    join rbac_role_binding b on b.subject_type = 'USER'
                        and b.subject_id = u.id
                        and b.role_code = :roleCode
                        and b.scope_type = 'PLATFORM'
                        and b.scope_id is null
                        and b.status = 'ENABLED'
                        and b.deleted_at is null
                    where u.status = 'ENABLED'
                      and u.deleted_at is null
                )
                """,
                Map.of("roleCode", SUPER_ADMIN_ROLE),
                Boolean.class
        );
        return Boolean.TRUE.equals(initialized);
    }

    @Override
    @Transactional
    public String createSuperAdmin(BootstrapUserDraft draft) {
        acquireBootstrapLock();
        if (hasSuperAdmin()) {
            throw new BusinessException(ErrorCode.CONFLICT, "超级管理员已初始化");
        }

        UUID roleId = requireRoleId(draft.roleCode());
        UUID userId = insertUser(draft);
        insertRoleBinding(roleId, userId, draft.roleCode());
        writeAuditLog(userId, "USER_CREATE", "iam_user", userId.toString());
        writeAuditLog(userId, "SUPER_ADMIN_INIT", "rbac_role_binding", userId.toString());
        return userId.toString();
    }

    private void acquireBootstrapLock() {
        jdbcTemplate.queryForObject(
                "select pg_advisory_xact_lock(hashtext(:lockName)) is null",
                Map.of("lockName", BOOTSTRAP_LOCK_NAME),
                Boolean.class
        );
    }

    private UUID requireRoleId(String roleCode) {
        try {
            return jdbcTemplate.queryForObject("""
                    select id
                    from rbac_role
                    where code = :roleCode
                      and status = 'ENABLED'
                      and deleted_at is null
                    """,
                    Map.of("roleCode", roleCode),
                    UUID.class
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "超级管理员角色未初始化，请先执行权限种子脚本");
        }
    }

    private UUID insertUser(BootstrapUserDraft draft) {
        return jdbcTemplate.queryForObject("""
                insert into iam_user (
                    username,
                    password_hash,
                    display_name,
                    email,
                    status,
                    must_change_password
                )
                values (
                    :username,
                    :passwordHash,
                    :displayName,
                    :email,
                    'ENABLED',
                    :mustChangePassword
                )
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("username", draft.username())
                        .addValue("passwordHash", draft.passwordHash())
                        .addValue("displayName", draft.displayName())
                        .addValue("email", draft.email())
                        .addValue("mustChangePassword", draft.mustChangePassword()),
                UUID.class
        );
    }

    private void insertRoleBinding(UUID roleId, UUID userId, String roleCode) {
        jdbcTemplate.update("""
                insert into rbac_role_binding (
                    subject_type,
                    subject_id,
                    role_id,
                    role_code,
                    scope_type,
                    scope_id,
                    status,
                    created_by,
                    updated_by
                )
                values (
                    'USER',
                    :userId,
                    :roleId,
                    :roleCode,
                    'PLATFORM',
                    null,
                    'ENABLED',
                    :userId,
                    :userId
                )
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("roleId", roleId)
                        .addValue("roleCode", roleCode)
        );
    }

    private void writeAuditLog(
            UUID actorUserId,
            String action,
            String resourceType,
            String resourceId
    ) {
        jdbcTemplate.update("""
                insert into audit_log (
                    trace_id,
                    actor_type,
                    actor_user_id,
                    action,
                    resource_type,
                    resource_id,
                    scope_type,
                    scope_id,
                    result,
                    after_json
                )
                values (
                    :traceId,
                    'SYSTEM',
                    :actorUserId,
                    :action,
                    :resourceType,
                    :resourceId,
                    'PLATFORM',
                    null,
                    'SUCCESS',
                    cast(:afterJson as jsonb)
                )
                """,
                new MapSqlParameterSource()
                        .addValue("traceId", TraceContext.getTraceId())
                        .addValue("actorUserId", actorUserId)
                        .addValue("action", action)
                        .addValue("resourceType", resourceType)
                        .addValue("resourceId", resourceId)
                        .addValue("afterJson", "{\"bootstrap\":true}")
        );
    }
}
