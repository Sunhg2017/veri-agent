package com.songhg.veri.agent.authorization.application;

import org.springframework.util.StringUtils;

public record ResourceScope(
        /** 权限资源范围类型，默认 PLATFORM，项目级为 PROJECT。 */
        String scopeType,
        /** 范围实例 ID；平台级范围固定为空。 */
        String scopeId
) {

    private static final String PLATFORM = "PLATFORM";
    private static final String PROJECT = "PROJECT";

    public ResourceScope {
        scopeType = StringUtils.hasText(scopeType) ? scopeType.trim().toUpperCase() : PLATFORM;
        scopeId = StringUtils.hasText(scopeId) ? scopeId.trim() : null;
        if (PLATFORM.equals(scopeType)) {
            scopeId = null;
        }
    }

    public static ResourceScope platform() {
        return new ResourceScope(PLATFORM, null);
    }

    public static ResourceScope project(String projectId) {
        return new ResourceScope(PROJECT, projectId);
    }

    public boolean isPlatform() {
        return PLATFORM.equals(scopeType);
    }

    public String auditResourceId(String permission) {
        if (isPlatform()) {
            return permission + "@PLATFORM";
        }
        return permission + "@" + scopeType + ":" + scopeId;
    }
}
