package com.songhg.veri.agent.modelaccess.security;

public record ServicePrincipal(
        /** 发起模型调用的服务编码 */
        String callerService,
        /** 被代理的终端用户 ID */
        String delegatedUserId
) {
}
