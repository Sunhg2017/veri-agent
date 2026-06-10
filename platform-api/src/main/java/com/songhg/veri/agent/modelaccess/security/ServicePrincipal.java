package com.songhg.veri.agent.modelaccess.security;

import java.util.List;

public record ServicePrincipal(
        /** 发起模型调用的服务编码 */
        String callerService,
        /** 被代理的终端用户 ID */
        String delegatedUserId,
        /** 用户态调用携带的角色编码；服务令牌调用为空，避免从 header 伪造角色策略上下文。 */
        List<String> roles
) {

    public ServicePrincipal {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public ServicePrincipal(String callerService, String delegatedUserId) {
        this(callerService, delegatedUserId, List.of());
    }
}
