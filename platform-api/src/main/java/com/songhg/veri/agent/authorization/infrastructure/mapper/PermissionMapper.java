package com.songhg.veri.agent.authorization.infrastructure.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PermissionMapper {

    List<String> permissionsForRoles(@Param("roles") List<String> roles);
}
