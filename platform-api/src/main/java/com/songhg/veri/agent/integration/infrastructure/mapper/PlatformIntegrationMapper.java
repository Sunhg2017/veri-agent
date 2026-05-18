package com.songhg.veri.agent.integration.infrastructure.mapper;

import com.songhg.veri.agent.integration.infrastructure.PlatformContextRow;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlatformIntegrationMapper {

    PlatformContextRow projectContextById(@Param("id") UUID id);

    PlatformContextRow projectContextByCode(@Param("code") String code);

    PlatformContextRow applicationContextById(@Param("id") UUID id);

    PlatformContextRow applicationContextByCode(@Param("code") String code);

    void insertServiceAuditEvent(
            @Param("traceId") String traceId,
            @Param("actorService") String actorService,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("scopeType") String scopeType,
            @Param("result") String result,
            @Param("afterJson") String afterJson,
            @Param("reason") String reason
    );
}
