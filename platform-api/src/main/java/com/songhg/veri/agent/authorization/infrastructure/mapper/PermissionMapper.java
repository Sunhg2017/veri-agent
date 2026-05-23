package com.songhg.veri.agent.authorization.infrastructure.mapper;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PermissionMapper {

    List<String> permissionsForRoles(@Param("roles") List<String> roles);

    boolean hasPermissionForScope(
            @Param("userId") UUID userId,
            @Param("permission") String permission,
            @Param("scopeType") String scopeType,
            @Param("scopeId") UUID scopeId
    );
}
