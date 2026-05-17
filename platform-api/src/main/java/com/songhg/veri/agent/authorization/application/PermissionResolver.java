package com.songhg.veri.agent.authorization.application;

import java.util.List;
import java.util.Set;

public interface PermissionResolver {

    Set<String> permissionsForRoles(List<String> roles);
}
