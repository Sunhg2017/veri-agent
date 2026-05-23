package com.songhg.veri.agent.common.error;

import org.springframework.security.access.AccessDeniedException;

public class PlatformAccessDeniedException extends AccessDeniedException {

    private final String permission;
    private final String resourceType;
    private final String resourceId;

    public PlatformAccessDeniedException(String permission) {
        this(permission, null, null);
    }

    public PlatformAccessDeniedException(String permission, String resourceType, String resourceId) {
        super("缺少权限：" + (permission == null ? "" : permission));
        this.permission = permission;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public ErrorCode getErrorCode() {
        return ErrorCode.FORBIDDEN;
    }

    public String getPermission() {
        return permission;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }
}
