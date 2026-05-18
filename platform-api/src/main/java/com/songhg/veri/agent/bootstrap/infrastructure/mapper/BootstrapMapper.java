package com.songhg.veri.agent.bootstrap.infrastructure.mapper;

import com.songhg.veri.agent.bootstrap.domain.BootstrapUserDraft;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BootstrapMapper {

    boolean hasSuperAdmin(@Param("roleCode") String roleCode);

    void acquireBootstrapLock(@Param("lockName") String lockName);

    UUID roleId(@Param("roleCode") String roleCode);

    UUID insertUser(BootstrapUserDraft draft);

    void insertRoleBinding(
            @Param("roleId") UUID roleId,
            @Param("userId") UUID userId,
            @Param("roleCode") String roleCode
    );

    void insertBootstrapAudit(
            @Param("traceId") String traceId,
            @Param("actorUserId") UUID actorUserId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId
    );
}
