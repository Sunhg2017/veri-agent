package com.songhg.veri.agent.common.audit.mapper;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditMapper {

    void insertAuditLog(
            @Param("traceId") String traceId,
            @Param("actorType") String actorType,
            @Param("actorUserId") UUID actorUserId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("result") String result,
            @Param("beforeJson") String beforeJson,
            @Param("afterJson") String afterJson,
            @Param("diffJson") String diffJson,
            @Param("reason") String reason
    );

    int cleanupAuditLogBefore(
            @Param("cutoff") Instant cutoff,
            @Param("batchSize") int batchSize
    );
}
