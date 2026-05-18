package com.songhg.veri.agent.bootstrap.infrastructure;

import com.songhg.veri.agent.bootstrap.domain.BootstrapStateStore;
import com.songhg.veri.agent.bootstrap.domain.BootstrapUserDraft;
import com.songhg.veri.agent.bootstrap.infrastructure.mapper.BootstrapMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.trace.TraceContext;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Profile("db")
@Repository
public class PostgresBootstrapStateStore implements BootstrapStateStore {

    private static final String SUPER_ADMIN_ROLE = "SuperAdmin";
    private static final String BOOTSTRAP_LOCK_NAME = "veri-agent:bootstrap:super-admin";

    private final BootstrapMapper mapper;

    public PostgresBootstrapStateStore(BootstrapMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean hasSuperAdmin() {
        return mapper.hasSuperAdmin(SUPER_ADMIN_ROLE);
    }

    @Override
    @Transactional
    public String createSuperAdmin(BootstrapUserDraft draft) {
        mapper.acquireBootstrapLock(BOOTSTRAP_LOCK_NAME);
        if (hasSuperAdmin()) {
            throw new BusinessException(ErrorCode.CONFLICT, "超级管理员已初始化");
        }

        UUID roleId = requireRoleId(draft.roleCode());
        UUID userId = mapper.insertUser(draft);
        mapper.insertRoleBinding(roleId, userId, draft.roleCode());
        writeAuditLog(userId, "USER_CREATE", "iam_user", userId.toString());
        writeAuditLog(userId, "SUPER_ADMIN_INIT", "rbac_role_binding", userId.toString());
        return userId.toString();
    }

    private UUID requireRoleId(String roleCode) {
        try {
            return mapper.roleId(roleCode);
        } catch (EmptyResultDataAccessException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "超级管理员角色未初始化，请先执行权限种子脚本");
        }
    }

    private void writeAuditLog(
            UUID actorUserId,
            String action,
            String resourceType,
            String resourceId
    ) {
        mapper.insertBootstrapAudit(
                TraceContext.getTraceId(),
                actorUserId,
                action,
                resourceType,
                resourceId
        );
    }
}
